package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import kr.yesulin.actor.stats.CompletionCounter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void analyzesThenGeneratesReopenableHwpWithConfirmedTextAndPhoto() throws Exception {
        DocumentStore store = new DocumentStore(
                temporaryDirectory.resolve("store"), Duration.ofMinutes(30), Clock.systemUTC());
        CompletionCounter counter = new CompletionCounter(temporaryDirectory.resolve("completed-count.txt"));
        PdfConverter pdf = new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp"), List.of());
        DocumentService service = new DocumentService(store, counter, pdf);
        MockMultipartFile hwp = new MockMultipartFile(
                "document", "공부의신_오디션지원서.hwp", "application/x-hwp",
                fixtureBytes("application.hwp"));
        AnalysisResponse analysis = service.analyze(hwp);
        FieldCandidate name = field(analysis, "이름", FieldCandidate.FieldKind.TEXT);
        FieldCandidate photo = field(analysis, "사진1", FieldCandidate.FieldKind.PHOTO);
        MockMultipartFile portrait = new MockMultipartFile(
                "portrait", "portrait.jpg", "image/jpeg", fixtureBytes("portrait.jpg"));
        GenerateRequest request = new GenerateRequest(
                List.of(new GenerateRequest.TextValue(name.id(), name.address(), "테스트배우")),
                List.of(new GenerateRequest.PhotoValue(photo.id(), photo.address(), "portrait")));

        GeneratedDocument generated = service.generate(
                analysis.documentId(), request, Map.of("portrait", portrait));

        Path output = temporaryDirectory.resolve("completed.hwp");
        Files.write(output, generated.content());
        HwpDocument reopened = HwpDocument.open(output);
        assertThat(reopened.cells())
                .filteredOn(cell -> cell.address().equals(name.address()))
                .extracting(CellSnapshot::text)
                .containsExactly("테스트배우");
        assertThat(reopened.embeddedImageCount()).isGreaterThan(0);
        assertThat(generated.fileName()).endsWith("_완성.hwp");
        ResumeResponse resumed = service.resume(analysis.documentId());
        assertThat(resumed.completed()).as("another browser can pick the finished file up").isTrue();
        assertThat(resumed.fileName()).isEqualTo(generated.fileName());

        service.generate(analysis.documentId(), request, Map.of("portrait", portrait));
        assertThat(counter.count()).as("same application regenerated counts once").isEqualTo(1);

        assumeTrue(pdf.available(), "rhwp not installed — run tools/install-rhwp.sh");
        GeneratedDocument rendered = service.completedPdf(analysis.documentId());
        assertThat(rendered.fileName()).endsWith("_완성.pdf");
        assertThat(new String(rendered.content(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void replacesAnswersAndPhotoOfAnAlreadyCompletedForm() throws Exception {
        DocumentStore store = new DocumentStore(
                temporaryDirectory.resolve("filled-store"), Duration.ofMinutes(30), Clock.systemUTC());
        DocumentService service = new DocumentService(store,
                new CompletionCounter(temporaryDirectory.resolve("filled-count.txt")),
                new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp"), List.of()));
        AnalysisResponse analysis = service.analyze(new MockMultipartFile(
                "document", "공부의신_완성.hwp", "application/x-hwp", fixtureBytes("gongbu-filled.hwp")));
        FieldCandidate name = field(analysis, "이름", FieldCandidate.FieldKind.TEXT);
        FieldCandidate photo = field(analysis, "사진 1", FieldCandidate.FieldKind.PHOTO);
        assertThat(HwpDocument.open(fixturePath("gongbu-filled.hwp")).drawingCount(photo.address())).isEqualTo(1);

        GeneratedDocument generated = service.generate(analysis.documentId(), new GenerateRequest(
                List.of(new GenerateRequest.TextValue(name.id(), name.address(), "김배우")),
                List.of(new GenerateRequest.PhotoValue(photo.id(), photo.address(), "portrait"))),
                Map.of("portrait", new MockMultipartFile("portrait", "portrait.jpg", "image/jpeg", fixtureBytes("portrait.jpg"))));

        Path output = temporaryDirectory.resolve("filled-completed.hwp");
        Files.write(output, generated.content());
        HwpDocument reopened = HwpDocument.open(output);
        assertThat(reopened.cells()).filteredOn(cell -> cell.address().equals(name.address()))
                .extracting(CellSnapshot::text).containsExactly("김배우");
        assertThat(reopened.drawingCount(photo.address())).as("old photo replaced, not stacked").isEqualTo(1);
    }

    @Test
    void listsFieldsInTheOrderTheFormReadsOnPaper() throws Exception {
        PdfConverter renderer = new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp"), List.of());
        assumeTrue(renderer.available(), "rhwp not installed — run tools/install-rhwp.sh");
        DocumentService service = new DocumentService(
                new DocumentStore(temporaryDirectory.resolve("order-store"), Duration.ofMinutes(30), Clock.systemUTC()),
                new CompletionCounter(temporaryDirectory.resolve("order-count.txt")), renderer);

        // 지금우리는: "작품경력" is the first table in the file but floats below "성명" on the page.
        List<FieldCandidate> fields = service.analyze(new MockMultipartFile(
                "document", "지금우리는.hwp", "application/x-hwp", fixtureBytes("jigeum.hwp"))).fields();

        assertThat(fields.getFirst().label()).isEqualTo("성명");
        assertThat(fields).extracting(FieldCandidate::label)
                .containsSubsequence("성명", "지방 공연 가능 여부", "년도 1", "자기 소개", "프로필 사진 1");
    }

    @Test
    void readsHwpxFormsLikeTheSameFormSavedAsHwp() throws Exception {
        PdfConverter renderer = new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp"), List.of());
        assumeTrue(renderer.available(), "rhwp not installed — run tools/install-rhwp.sh");
        DocumentService service = new DocumentService(
                new DocumentStore(temporaryDirectory.resolve("hwpx-store"), Duration.ofMinutes(30), Clock.systemUTC()),
                new CompletionCounter(temporaryDirectory.resolve("hwpx-count.txt")), renderer);

        AnalysisResponse hwpx = service.analyze(new MockMultipartFile(
                "document", "한강대.hwpx", "application/octet-stream", fixtureBytes("hangang.hwpx")));
        AnalysisResponse hwp = service.analyze(new MockMultipartFile(
                "document", "한강대.hwp", "application/x-hwp", fixtureBytes("hangang.hwp")));

        assertThat(hwpx.fields()).extracting(FieldCandidate::label)
                .containsExactlyElementsOf(hwp.fields().stream().map(FieldCandidate::label).toList());
        FieldCandidate name = field(hwpx, "이름", FieldCandidate.FieldKind.TEXT);
        GeneratedDocument generated = service.generate(hwpx.documentId(), new GenerateRequest(
                List.of(new GenerateRequest.TextValue(name.id(), name.address(), "홍길동")), List.of()), Map.of());
        assertThat(generated.fileName()).isEqualTo("한강대_완성.hwp");
    }

    @Test
    void removesExpiredOrphanDirectoryWhenStoreStarts() throws Exception {
        Path root = temporaryDirectory.resolve("orphan-store");
        Path orphan = root.resolve("old-document");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("source.hwp"), "expired");
        Instant createdAt = Files.readAttributes(
                orphan, java.nio.file.attribute.BasicFileAttributes.class).creationTime().toInstant();
        Instant now = createdAt.plus(Duration.ofHours(1));

        new DocumentStore(root, Duration.ofMinutes(30), Clock.fixed(now, ZoneOffset.UTC));

        assertThat(orphan).doesNotExist();
    }

    @Test
    void removesOrphanThatExpiresAfterStoreRestart() throws Exception {
        Path root = temporaryDirectory.resolve("later-orphan-store");
        Path orphan = root.resolve("recent-document");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("source.hwp"), "recent");
        Instant createdAt = Files.readAttributes(
                orphan, java.nio.file.attribute.BasicFileAttributes.class).creationTime().toInstant();
        MutableClock clock = new MutableClock(createdAt.plus(Duration.ofMinutes(10)));
        DocumentStore restartedStore = new DocumentStore(root, Duration.ofMinutes(30), clock);
        assertThat(orphan).exists();

        clock.set(createdAt.plus(Duration.ofMinutes(31)));
        restartedStore.removeExpired();

        assertThat(orphan).doesNotExist();
    }

    private static FieldCandidate field(
            AnalysisResponse analysis, String label, FieldCandidate.FieldKind kind) {
        return analysis.fields().stream()
                .filter(candidate -> candidate.label().equals(label) && candidate.kind() == kind)
                .findFirst()
                .orElseThrow();
    }

    private static Path fixturePath(String name) throws Exception {
        return Path.of(DocumentServiceTest.class.getResource("/fixtures/" + name).toURI());
    }

    private static byte[] fixtureBytes(String name) throws Exception {
        try (var input = DocumentServiceTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return input.readAllBytes();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
