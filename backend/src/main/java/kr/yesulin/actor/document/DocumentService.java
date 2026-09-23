package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.yesulin.actor.stats.CompletionCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Logs ids, sizes and counts only: never file names, field labels or values, which can identify an applicant. */
@Service
public final class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private final DocumentStore store;
    private final FieldExtractor extractor;
    private final CompletionCounter counter;
    private final PdfConverter pdfConverter;

    public DocumentService(DocumentStore store, CompletionCounter counter, PdfConverter pdfConverter) {
        this.store = store;
        this.counter = counter;
        this.pdfConverter = pdfConverter;
        this.extractor = new FieldExtractor();
    }

    public AnalysisResponse analyze(MultipartFile upload) throws IOException, HwpDocumentException {
        long started = System.nanoTime();
        UploadValidator.HangulUpload hangul = UploadValidator.hangul(upload);
        byte[] content = hangul.format() == UploadValidator.Format.HWPX ? fromHwpx(hangul.bytes()) : hangul.bytes();
        StoredDocument stored = store.create(upload.getOriginalFilename(), content);
        try {
            HwpDocument document = HwpDocument.open(stored.source());
            List<CellSnapshot> cells = document.cells();
            List<FieldCandidate> fields = TableReadingOrder.sort(
                    extractor.extract(cells), cells, pdfConverter, stored.source());
            stored = store.attachFields(stored.id(), fields);
            int tableCount = (int) cells.stream()
                    .map(cell -> cell.address().tableIndex())
                    .distinct()
                    .count();
            log.info("analyzed document={} bytes={} tables={} cells={} textFields={} photoFields={} {}ms",
                    stored.id(), content.length, tableCount, cells.size(),
                    fields.stream().filter(field -> field.kind() == FieldCandidate.FieldKind.TEXT).count(),
                    fields.stream().filter(field -> field.kind() == FieldCandidate.FieldKind.PHOTO).count(),
                    elapsed(started));
            if (fields.isEmpty()) {
                log.warn("no fields found document={} tables={} cells={}", stored.id(), tableCount, cells.size());
            }
            return new AnalysisResponse(
                    stored.id(), stored.originalName(), stored.expiresAt(), tableCount, cells.size(), fields);
        } catch (HwpDocumentException | RuntimeException exception) {
            log.warn("analyze failed document={} bytes={} {}: {}", stored.id(), content.length,
                    exception.getClass().getSimpleName(), exception.getMessage());
            store.remove(stored.id());
            throw exception;
        }
    }

    public GeneratedDocument generate(
            UUID documentId,
            GenerateRequest request,
            Map<String, MultipartFile> uploadedPhotos) throws IOException, HwpDocumentException {
        long started = System.nanoTime();
        StoredDocument stored = store.require(documentId);
        CompletedFileName fileName = CompletedFileName.chosenOrDefault(request.fileName(), stored.originalName());
        Map<String, FieldCandidate> allowed = stored.fields().stream()
                .collect(Collectors.toUnmodifiableMap(FieldCandidate::id, Function.identity()));
        HwpDocument document = HwpDocument.open(stored.source());

        for (GenerateRequest.TextValue value : request.textValues()) {
            requireCandidate(allowed, value.fieldId(), value.address(), FieldCandidate.FieldKind.TEXT);
            String text = value.value() == null ? "" : value.value();
            if (allowed.get(value.fieldId()).style() == FieldCandidate.InputStyle.APPEND) {
                document.appendText(value.address(), text);
            } else {
                document.setText(value.address(), text);
            }
        }
        for (GenerateRequest.PhotoValue photo : request.photos()) {
            requireCandidate(allowed, photo.fieldId(), photo.address(), FieldCandidate.FieldKind.PHOTO);
            MultipartFile upload = uploadedPhotos.get(photo.fileKey());
            if (upload == null) {
                throw new IllegalArgumentException("사진 파일이 없습니다: " + photo.fileKey());
            }
            String extension = UploadValidator.image(upload);
            Path photoPath = writePhoto(stored, upload, extension);
            document.insertImage(photo.address(), photoPath);
        }

        Path draft = stored.directory().resolve("completed-" + UUID.randomUUID() + ".hwp");
        document.save(draft);
        boolean firstCompletion = !Files.exists(completedPath(stored));
        Files.move(draft, completedPath(stored), StandardCopyOption.REPLACE_EXISTING);
        store.attachCompletedFileName(documentId, fileName);
        Files.deleteIfExists(pdfPath(stored));
        PreviewService.invalidate(stored);
        if (firstCompletion) {
            // Re-generating after an edit is the same application, so it counts once.
            counter.increment();
        }
        log.info("generated document={} texts={} photos={} first={} bytes={} {}ms", documentId,
                request.textValues().size(), request.photos().size(), firstCompletion,
                Files.size(completedPath(stored)), elapsed(started));
        return completed(documentId);
    }

    /** Lets the system browser pick up a form completed in an in-app browser (same 30-minute lifetime). */
    public ResumeResponse resume(UUID documentId) {
        StoredDocument stored = store.require(documentId);
        return new ResumeResponse(documentId.toString(), stored.completedFileName().hwp(),
                Files.exists(completedPath(stored)));
    }

    /** Serves the latest completed file over a plain GET so in-app browsers can hand it to their download manager. */
    public GeneratedDocument completed(UUID documentId) throws IOException {
        StoredDocument stored = store.require(documentId);
        Path path = completedPath(stored);
        if (!Files.exists(path)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        return new GeneratedDocument(stored.completedFileName().hwp(), Files.readAllBytes(path));
    }

    /** Renders the latest completed HWP to PDF once and reuses it until the HWP is regenerated. */
    public GeneratedDocument completedPdf(UUID documentId) throws IOException, HwpDocumentException {
        StoredDocument stored = store.require(documentId);
        Path hwp = completedPath(stored);
        if (!Files.exists(hwp)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        Path pdf = pdfPath(stored);
        if (!Files.exists(pdf)) {
            Path draft = stored.directory().resolve("completed-" + UUID.randomUUID() + ".pdf");
            long started = System.nanoTime();
            try {
                pdfConverter.convert(hwp, draft);
                Files.move(draft, pdf, StandardCopyOption.REPLACE_EXISTING);
                log.info("pdf rendered document={} bytes={} {}ms", documentId, Files.size(pdf), elapsed(started));
            } catch (HwpDocumentException | IOException | RuntimeException exception) {
                log.warn("pdf failed document={} {}: {}", documentId,
                        exception.getClass().getSimpleName(), exception.getMessage());
                throw exception;
            } finally {
                Files.deleteIfExists(draft);
            }
        }
        return new GeneratedDocument(stored.completedFileName().pdf(), Files.readAllBytes(pdf));
    }

    /** HWPX forms are turned into HWP 5 once, on upload; everything after works on the HWP. */
    private byte[] fromHwpx(byte[] hwpx) throws IOException, HwpDocumentException {
        if (!pdfConverter.available()) {
            throw new HwpDocumentException("지금은 HWPX 파일을 처리할 수 없습니다. 한글에서 HWP로 저장해 올려 주세요.");
        }
        Path work = Files.createTempDirectory("yesulin-hwpx-");
        try {
            Path source = work.resolve("source.hwpx");
            Path hwp = work.resolve("source.hwp");
            Files.write(source, hwpx);
            long started = System.nanoTime();
            pdfConverter.toHwp(source, hwp);
            log.info("converted hwpx bytes={} {}ms", hwpx.length, elapsed(started));
            return Files.readAllBytes(hwp);
        } finally {
            try (var files = Files.list(work)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(work);
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static Path pdfPath(StoredDocument stored) {
        return stored.directory().resolve("completed.pdf");
    }

    private static Path completedPath(StoredDocument stored) {
        return stored.directory().resolve("completed.hwp");
    }

    private static void requireCandidate(
            Map<String, FieldCandidate> allowed,
            String fieldId,
            CellAddress address,
            FieldCandidate.FieldKind kind) {
        FieldCandidate candidate = allowed.get(fieldId);
        if (candidate == null || candidate.kind() != kind || !candidate.address().equals(address)) {
            throw new IllegalArgumentException("분석 결과와 일치하지 않는 입력 위치입니다: " + fieldId);
        }
    }

    private static Path writePhoto(StoredDocument stored, MultipartFile upload, String extension) throws IOException {
        Path path = stored.directory().resolve("photo-" + UUID.randomUUID() + extension).normalize();
        if (!path.startsWith(stored.directory())) {
            throw new IllegalArgumentException("잘못된 사진 경로입니다.");
        }
        Files.write(path, upload.getBytes());
        return path;
    }

}
