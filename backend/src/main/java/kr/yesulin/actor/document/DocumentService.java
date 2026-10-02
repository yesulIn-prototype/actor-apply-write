package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
    private final PdfConverter pdfConverter;
    private final CompletedDocumentWriter writer;

    public DocumentService(DocumentStore store, CompletionCounter counter, PdfConverter pdfConverter) {
        this.store = store;
        this.pdfConverter = pdfConverter;
        this.extractor = new FieldExtractor();
        this.writer = new CompletedDocumentWriter(store, counter, pdfConverter);
    }

    public AnalysisResponse analyze(MultipartFile upload) throws IOException, HwpDocumentException {
        long started = System.nanoTime();
        byte[] content = hwpSource(upload);
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
        List<CellWrite> writes = new ArrayList<>();
        for (GenerateRequest.TextValue value : request.textValues()) {
            requireCandidate(allowed, value.fieldId(), value.address(), FieldCandidate.FieldKind.TEXT);
            String text = value.value() == null ? "" : value.value();
            writes.add(allowed.get(value.fieldId()).style() == FieldCandidate.InputStyle.APPEND
                    ? new CellWrite.Append(value.address(), text)
                    : new CellWrite.Replace(value.address(), text));
        }
        for (GenerateRequest.PhotoValue photo : request.photos()) {
            requireCandidate(allowed, photo.fieldId(), photo.address(), FieldCandidate.FieldKind.PHOTO);
            MultipartFile upload = uploadedPhotos.get(photo.fileKey());
            if (upload == null) {
                throw new IllegalArgumentException("사진 파일이 없습니다: " + photo.fileKey());
            }
            writes.add(new CellWrite.Photo(photo.address(), upload));
        }
        boolean first = writer.write(stored, fileName, List.of(), writes, true);
        log.info("generated document={} texts={} photos={} first={} bytes={} {}ms", documentId,
                request.textValues().size(), request.photos().size(), first,
                Files.size(stored.completedHwp()), elapsed(started));
        return completed(documentId);
    }

    /**
     * Gives one applicant their own copy of a shared form. The shared file is never written to; every
     * later build of this job reads the copy and replaces only this job's completed file.
     */
    public UUID startJob(String originalName, Path sharedSource, String owner, List<EditTarget> targets)
            throws IOException {
        StoredDocument stored = store.create(originalName, Files.readAllBytes(sharedSource));
        store.attachOwner(stored.id(), owner, targets);
        log.info("job started document={} owner={}", stored.id(), owner);
        return stored.id();
    }

    /**
     * Builds a job started from a shared form; another form's job id is treated as unknown.
     *
     * @param growths table rows to add before writing, for answers longer than the form's rows
     * @param counted false for an operator's test build, which is not an application
     */
    public GeneratedDocument buildJob(UUID documentId, String owner, String fileName, List<TableGrowth> growths,
            List<CellWrite> writes, boolean counted)
            throws IOException, HwpDocumentException {
        long started = System.nanoTime();
        StoredDocument stored = store.require(documentId);
        if (!stored.owner().equals(owner)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        boolean first = writer.write(stored, CompletedFileName.chosenOrDefault(fileName, stored.originalName()),
                growths, writes, counted);
        log.info("generated job={} owner={} cells={} addedRows={} first={} {}ms", documentId, owner, writes.size(),
                growths.stream().mapToInt(TableGrowth::count).sum(), first, elapsed(started));
        return completed(documentId);
    }

    /** Checks an uploaded Hangul file and returns it as HWP 5, converting HWPX once. */
    public byte[] hwpSource(MultipartFile upload) throws IOException, HwpDocumentException {
        UploadValidator.HangulUpload hangul = UploadValidator.hangul(upload);
        return hangul.format() == UploadValidator.Format.HWPX ? fromHwpx(hangul.bytes()) : hangul.bytes();
    }

    /** Lets the system browser pick up a form completed in an in-app browser (same 30-minute lifetime). */
    public ResumeResponse resume(UUID documentId) {
        StoredDocument stored = store.require(documentId);
        return new ResumeResponse(documentId.toString(), stored.completedFileName().hwp(),
                Files.exists(stored.completedHwp()));
    }

    /** Serves the latest completed file over a plain GET so in-app browsers can hand it to their download manager. */
    public GeneratedDocument completed(UUID documentId) throws IOException {
        StoredDocument stored = store.require(documentId);
        Path path = stored.completedHwp();
        if (!Files.exists(path)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        return new GeneratedDocument(stored.completedFileName().hwp(), Files.readAllBytes(path));
    }

    /** Renders the latest completed HWP to PDF once and reuses it until the HWP is regenerated. */
    public GeneratedDocument completedPdf(UUID documentId) throws IOException, HwpDocumentException {
        StoredDocument stored = store.require(documentId);
        Path hwp = stored.completedHwp();
        if (!Files.exists(hwp)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        Path pdf = stored.completedPdf();
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
}
