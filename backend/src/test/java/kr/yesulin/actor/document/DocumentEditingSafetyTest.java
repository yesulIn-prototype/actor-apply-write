package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class DocumentEditingSafetyTest {
    @TempDir Path work;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void failedRenderingKeepsPreviousFileAndPdfWhenEditCannotBeValidated() throws Exception {
        // given: a rendered original, but a renderer which rejects a candidate edit
        DocumentStore store = store();
        StoredDocument job = job(store);
        Path rhwp = Path.of("../tools/rhwp/rhwp/rhwp.exe");
        var real = new PreviewService(store, new PdfConverter(rhwp, List.of()), json);
        var before = new DocumentEditingService(store, real).regions(job.id(), job.editToken());
        Files.writeString(job.completedPdf(), "old pdf");
        byte[] original = Files.readAllBytes(job.completedHwp());
        var failing = new PdfConverter(rhwp, List.of()) {
            @Override public void exportSvg(Path input, Path output) throws IOException, HwpDocumentException {
                throw new HwpDocumentException("synthetic renderer failure");
            }
        };
        var editor = new DocumentEditingService(store, new PreviewService(store, failing, json));
        // when / then
        assertThatThrownBy(() -> editor.edit(job.id(), job.editToken(), request(before, "새 글")))
                .isInstanceOf(HwpDocumentException.class);
        assertThat(Files.readAllBytes(job.completedHwp())).isEqualTo(original);
        assertThat(Files.readString(job.completedPdf())).isEqualTo("old pdf");
        try (var files = Files.list(job.directory())) {
            assertThat(files.map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith("edit-"));
        }
    }

    @Test
    void onlyOneConcurrentEditWinsWhenBothUseSameRevision() throws Exception {
        // given
        var store = store();
        var job = job(store);
        var other = job(store);
        byte[] untouched = Files.readAllBytes(other.completedHwp());
        var editor = new DocumentEditingService(store, new PreviewService(store,
                new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp.exe"), List.of()), json));
        var before = editor.regions(job.id(), job.editToken());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        // when: both requests start before either receives a new manifest
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.of("first", "second").stream().map(text -> executor.submit(() -> {
                ready.countDown(); start.await();
                try { editor.edit(job.id(), job.editToken(), request(before, text)); return "saved"; }
                catch (DocumentEdits.Conflict conflict) { return "conflict"; }
            })).toList();
            ready.await(); start.countDown();
            assertThat(List.of(tasks.get(0).get(), tasks.get(1).get())).containsExactlyInAnyOrder("saved", "conflict");
        }
        // then: another applicant and the original template are not affected
        assertThat(Files.readAllBytes(other.completedHwp())).isEqualTo(untouched);
        assertThat(Files.readAllBytes(job.source())).isEqualTo(untouched);
    }

    @Test
    void cannotEditUnmappedTargetOrMissingJobOrUseAnotherJobsCapability() throws Exception {
        // given
        var store = store();
        var job = job(store);
        var other = job(store);
        var editor = new DocumentEditingService(store, new PreviewService(store,
                new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp.exe"), List.of()), json));
        var before = editor.regions(job.id(), job.editToken());
        byte[] original = Files.readAllBytes(job.completedHwp());
        // when / then
        assertThatThrownBy(() -> editor.regions(job.id(), other.editToken())).isInstanceOf(DocumentEdits.Forbidden.class);
        assertThatThrownBy(() -> editor.regions(job.id(), null)).isInstanceOf(DocumentEdits.Forbidden.class);
        assertThatThrownBy(() -> editor.edit(job.id(), job.editToken(), new DocumentEdits.Request(before.revision(),
                List.of(new DocumentEdits.Change("c:99.0.0", "wrong", true)))))
                .isInstanceOf(DocumentEdits.Invalid.class);
        assertThat(Files.readAllBytes(job.completedHwp())).isEqualTo(original);
        store.remove(job.id());
        assertThatThrownBy(() -> editor.regions(job.id(), job.editToken())).isInstanceOf(DocumentStore.DocumentNotFoundException.class);
    }

    @Test
    void editingDoesNotExtendTheOriginalThirtyMinuteLifetime() throws Exception {
        var clock = new TestClock();
        var store = new DocumentStore(work, Duration.ofMinutes(30), clock);
        var job = job(store);
        var editor = new DocumentEditingService(store, new PreviewService(store,
                new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp.exe"), List.of()), json));
        clock.now = clock.now.plus(Duration.ofMinutes(29));
        var before = editor.regions(job.id(), job.editToken());
        editor.edit(job.id(), job.editToken(), request(before, "만료 전 수정"));
        assertThat(store.require(job.id()).expiresAt()).isEqualTo(job.expiresAt());
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThatThrownBy(() -> editor.regions(job.id(), job.editToken()))
                .isInstanceOf(DocumentStore.DocumentNotFoundException.class);
        assertThat(Files.exists(job.directory())).isFalse();
    }

    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-04T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    }

    private DocumentStore store() throws IOException {
        return new DocumentStore(work, Duration.ofMinutes(30), Clock.systemUTC());
    }
    private StoredDocument job(DocumentStore store) throws Exception {
        Path fixture = Path.of(getClass().getResource("/forms/sample-notice.hwp").toURI());
        var job = store.create("지원서.hwp", Files.readAllBytes(fixture));
        Files.copy(job.source(), job.completedHwp());
        return job;
    }
    private static DocumentEdits.Request request(DocumentEdits.View view, String text) {
        return new DocumentEdits.Request(view.revision(), List.of(new DocumentEdits.Change("c:0.0.1", text, true)));
    }
}
