package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import kr.yesulin.actor.stats.CompletionCounter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

class RecentFormsAcceptanceTest {
    @TempDir
    Path temporaryDirectory;

    @TestFactory
    Stream<DynamicTest> generatesReopenableHwpForEachProvidedForm() throws Exception {
        String configured = System.getenv("YESULIN_FORMS_DIR");
        assumeTrue(configured != null && !configured.isBlank(), "Set YESULIN_FORMS_DIR for the local form corpus");
        Path directory = Path.of(configured);
        List<Path> forms;
        try (Stream<Path> entries = Files.list(directory)) {
            forms = entries.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".hwp"))
                    .sorted()
                    .toList();
        }
        assertThat(forms).as("provided HWP forms").hasSize(8);
        return forms.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> verify(path)));
    }

    private void verify(Path source) throws Exception {
        Path work = temporaryDirectory.resolve(String.valueOf(Math.abs(source.getFileName().toString().hashCode())));
        DocumentStore store = new DocumentStore(work.resolve("store"), Duration.ofMinutes(30), Clock.systemUTC());
        CompletionCounter counter = new CompletionCounter(work.resolve("count.txt"));
        PdfConverter renderer = new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp"), List.of());
        DocumentService service = new DocumentService(store, counter, renderer);
        MockMultipartFile upload = new MockMultipartFile(
                "document", source.getFileName().toString(), "application/x-hwp", Files.readAllBytes(source));

        AnalysisResponse analysis = service.analyze(upload);
        assertThat(analysis.fields()).as("editable fields in " + source.getFileName()).isNotEmpty();
        FieldCandidate text = analysis.fields().stream()
                .filter(field -> field.kind() == FieldCandidate.FieldKind.TEXT)
                .filter(field -> field.label().matches("^(이름|성명)(\\b|\\s|\\(|$).*$"))
                .findFirst()
                .orElseGet(() -> analysis.fields().stream()
                        .filter(field -> field.kind() == FieldCandidate.FieldKind.TEXT)
                        .filter(field -> field.style() == FieldCandidate.InputStyle.BLANK)
                        .findFirst().orElseThrow());
        FieldCandidate photo = analysis.fields().stream()
                .filter(field -> field.kind() == FieldCandidate.FieldKind.PHOTO)
                .findFirst().orElse(null);
        if (source.getFileName().toString().contains("한강대")) {
            assertThat(photo).as("unlabeled portrait slot in the first page").isNotNull();
        }
        String marker = "검증배우";
        List<GenerateRequest.PhotoValue> photoValues = photo == null ? List.of()
                : List.of(new GenerateRequest.PhotoValue(photo.id(), photo.address(), "portrait"));
        Map<String, MockMultipartFile> uploads = photo == null ? Map.of()
                : Map.of("portrait", new MockMultipartFile(
                        "portrait", "portrait.jpg", "image/jpeg", portraitBytes()));
        GenerateRequest request = new GenerateRequest(
                List.of(new GenerateRequest.TextValue(text.id(), text.address(), marker)), photoValues);

        GeneratedDocument generated = service.generate(analysis.documentId(), request, Map.copyOf(uploads));
        Path output = work.resolve("completed.hwp");
        Files.write(output, generated.content());
        HwpDocument original = HwpDocument.open(source);
        HwpDocument reopened = HwpDocument.open(output);
        assertThat(reopened.cells()).as("table cell count in " + source.getFileName())
                .hasSameSizeAs(original.cells());
        Map<CellAddress, String> unchanged = original.cells().stream()
                .filter(cell -> !cell.address().equals(text.address()))
                .filter(cell -> photo == null || !cell.address().equals(photo.address()))
                .collect(java.util.stream.Collectors.toMap(CellSnapshot::address, CellSnapshot::text));
        assertThat(reopened.cells().stream()
                .filter(cell -> unchanged.containsKey(cell.address()))
                .collect(java.util.stream.Collectors.toMap(CellSnapshot::address, CellSnapshot::text)))
                .as("unchanged form cells in " + source.getFileName()).isEqualTo(unchanged);
        assertThat(reopened.cells()).filteredOn(cell -> cell.address().equals(text.address()))
                .extracting(CellSnapshot::text).as("filled text in " + source.getFileName())
                .anySatisfy(value -> assertThat(value).contains(marker));
        if (photo != null) {
            assertThat(reopened.embeddedImageCount()).as("embedded photo in " + source.getFileName())
                    .isGreaterThan(original.embeddedImageCount());
            if (source.getFileName().toString().contains("한강대")) {
                assertThat(reopened.cells()).filteredOn(cell -> cell.address().equals(photo.address()))
                        .allSatisfy(cell -> {
                            assertThat(cell.width()).isGreaterThan(5_000);
                            assertThat(cell.height()).isGreaterThan(20_000);
                        });
            }
        }
        Path rendered = work.resolve("completed.pdf");
        renderer.convert(output, rendered);
        assertThat(Files.readAllBytes(rendered)).as("renderable PDF in " + source.getFileName())
                .startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        Path originalPages = work.resolve("original-pages");
        Path completedPages = work.resolve("completed-pages");
        renderer.exportSvg(source, originalPages);
        renderer.exportSvg(output, completedPages);
        long beforePages;
        long afterPages;
        try (Stream<Path> before = Files.list(originalPages); Stream<Path> after = Files.list(completedPages)) {
            beforePages = before.filter(path -> path.toString().endsWith(".svg")).count();
            afterPages = after.filter(path -> path.toString().endsWith(".svg")).count();
        }
        assertThat(afterPages).as("page count in " + source.getFileName()).isEqualTo(beforePages);
        PreviewResponse preview = new PreviewService(store, renderer, JsonMapper.builder().build())
                .preview(analysis.documentId());
        assertThat(preview.pages()).as("completed preview pages in " + source.getFileName())
                .hasSize((int) afterPages);
        assertThat(preview.hotspots()).as("editable preview field in " + source.getFileName())
                .anySatisfy(hotspot -> assertThat(hotspot.fieldId()).isEqualTo(text.id()));
        String reviewDirectory = System.getenv("YESULIN_QA_OUTPUT");
        if (reviewDirectory != null && !reviewDirectory.isBlank()) {
            Path review = Path.of(reviewDirectory);
            Files.createDirectories(review);
            String id = work.getFileName().toString();
            Path originalPdf = work.resolve("original.pdf");
            renderer.convert(source, originalPdf);
            Files.copy(originalPdf, review.resolve(id + "-original.pdf"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(rendered, review.resolve(id + "-completed.pdf"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.printf("REVIEW %s: %s%n", source.getFileName(), id);
        }
        System.out.printf("FORM %s: tables=%d cells=%d text=%d photos=%d chosen=%s%n",
                source.getFileName(), analysis.tableCount(), analysis.cellCount(),
                analysis.fields().stream().filter(field -> field.kind() == FieldCandidate.FieldKind.TEXT).count(),
                analysis.fields().stream().filter(field -> field.kind() == FieldCandidate.FieldKind.PHOTO).count(),
                text.label());
        System.out.printf("PAGES %s: original=%d completed=%d%n", source.getFileName(), beforePages, afterPages);
    }

    private static byte[] portraitBytes() throws Exception {
        try (var input = RecentFormsAcceptanceTest.class.getResourceAsStream("/fixtures/portrait.jpg")) {
            if (input == null) {
                throw new IllegalStateException("Missing portrait fixture");
            }
            return input.readAllBytes();
        }
    }
}
