package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import kr.yesulin.actor.stats.CompletionCounter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Writes answers into a fresh copy of the applicant's source and makes it their completed file.
 * The source itself is only read, so building again starts from the blank form every time.
 */
final class CompletedDocumentWriter {
    private final DocumentStore store;
    private final CompletionCounter counter;
    private final RowInserter rows;

    CompletedDocumentWriter(DocumentStore store, CompletionCounter counter, PdfConverter rhwp) {
        this.store = store;
        this.counter = counter;
        this.rows = new RowInserter(rhwp);
    }

    /**
     * Returns whether this was the document's first completion.
     *
     * @param growths rows to add first; the writes already address the grown tables
     * @param counted an applicant's completion; an operator's test build is not counted
     */
    boolean write(StoredDocument stored, CompletedFileName fileName, List<TableGrowth> growths,
            List<CellWrite> writes, boolean counted) throws IOException, HwpDocumentException {
        if (growths.isEmpty()) {
            return write(stored, HwpDocument.open(stored.source()), fileName, writes, counted);
        }
        Path grown = rows.grow(stored.source(), stored.directory(), growths);
        try {
            return write(stored, HwpDocument.open(grown), fileName, writes, counted);
        } finally {
            Files.deleteIfExists(grown);
        }
    }

    private boolean write(StoredDocument stored, HwpDocument document, CompletedFileName fileName,
            List<CellWrite> writes, boolean counted) throws IOException, HwpDocumentException {
        for (CellWrite write : writes) {
            switch (write) {
                case CellWrite.Replace replace -> document.setText(replace.address(), replace.text());
                case CellWrite.Append append -> document.appendText(append.address(), append.text());
                case CellWrite.Photo photo -> document.insertImage(photo.address(), savePhoto(stored, photo.image()));
            }
        }
        Path draft = stored.directory().resolve("completed-" + UUID.randomUUID() + ".hwp");
        document.save(draft);
        boolean firstCompletion = !Files.exists(stored.completedHwp());
        Files.move(draft, stored.completedHwp(), StandardCopyOption.REPLACE_EXISTING);
        store.attachCompletedFileName(stored.id(), fileName);
        Files.deleteIfExists(stored.completedPdf());
        PreviewService.invalidate(stored);
        if (firstCompletion && counted) {
            // Re-generating after an edit is the same application, so it counts once.
            counter.increment();
        }
        return firstCompletion;
    }

    private static Path savePhoto(StoredDocument stored, MultipartFile upload) throws IOException {
        String extension = UploadValidator.image(upload);
        Path path = stored.directory().resolve("photo-" + UUID.randomUUID() + extension).normalize();
        if (!path.startsWith(stored.directory())) {
            throw new IllegalArgumentException("잘못된 사진 경로입니다.");
        }
        Files.write(path, upload.getBytes());
        return path;
    }
}
