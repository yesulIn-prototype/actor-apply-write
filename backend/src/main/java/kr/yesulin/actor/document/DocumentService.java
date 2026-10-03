package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import kr.yesulin.actor.stats.CompletionCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Logs sizes, counts and times only: never job ids (one is enough to download the finished file), file names,
 * field labels or values, which can identify an applicant.
 */
@Service
public final class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private final DocumentStore store;
    private final PdfConverter pdfConverter;
    private final CompletedDocumentWriter writer;

    public DocumentService(DocumentStore store, CompletionCounter counter, PdfConverter pdfConverter) {
        this.store = store;
        this.pdfConverter = pdfConverter;
        this.writer = new CompletedDocumentWriter(store, counter, pdfConverter);
    }

    /**
     * Gives one applicant their own copy of a shared form. The shared file is never written to; every
     * later build of this job reads the copy and replaces only this job's completed file.
     */
    public UUID startJob(String originalName, Path sharedSource, String owner) throws IOException {
        StoredDocument stored = store.create(originalName, Files.readAllBytes(sharedSource));
        store.attachOwner(stored.id(), owner);
        log.info("job started owner={}", owner);
        return stored.id();
    }

    /**
     * Builds a job started from a shared form; another form's job id is treated as unknown.
     *
     * @param content rows to add, cells to write, and where each answer is edited from the preview of this build
     * @param counted false for an operator's test build, which is not an application
     */
    public GeneratedDocument buildJob(UUID documentId, String owner, String fileName, JobContent content,
            boolean counted)
            throws IOException, HwpDocumentException {
        long started = System.nanoTime();
        StoredDocument stored = store.require(documentId);
        if (!stored.owner().equals(owner)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        boolean first = writer.write(stored, CompletedFileName.chosenOrDefault(fileName, stored.originalName()),
                content.growths(), content.writes(), counted);
        store.attachTargets(documentId, content.targets());
        log.info("generated job owner={} cells={} addedRows={} first={} {}ms", owner,
                content.writes().size(), content.growths().stream().mapToInt(TableGrowth::count).sum(), first,
                elapsed(started));
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
                log.info("pdf rendered bytes={} {}ms", Files.size(pdf), elapsed(started));
            } catch (HwpDocumentException | IOException | RuntimeException exception) {
                log.warn("pdf failed {}: {}", exception.getClass().getSimpleName(),
                        JobIds.masked(exception.getMessage()));
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
}
