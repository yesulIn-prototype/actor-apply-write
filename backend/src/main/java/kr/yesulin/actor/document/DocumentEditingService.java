package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Private-file editing. Each job's stable directory object serializes generation, editing and downloads. */
@Service
public final class DocumentEditingService {
    private final DocumentStore store;
    private final PreviewService previews;

    public DocumentEditingService(DocumentStore store, PreviewService previews) {
        this.store = store;
        this.previews = previews;
    }

    public DocumentEdits.View regions(UUID id, String token) throws IOException, HwpDocumentException {
        synchronized (store.require(id).directory()) {
            StoredDocument job = authorized(id, token);
            var texts = new HwpTextEditor(job.completedHwp()).texts();
            Path directory = job.directory().resolve("preview");
            var layout = previews.layout(job.completedHwp(), directory);
            var boxes = DocumentRegionLayout.boxes(layout, directory);
            var regions = texts.entrySet().stream().filter(entry -> boxes.containsKey(entry.getKey()))
                    .map(entry -> new DocumentEdits.Region(entry.getKey(), entry.getValue(), List.copyOf(boxes.get(entry.getKey()))))
                    .toList();
            return new DocumentEdits.View(revision(job.completedHwp()), layout.pages(), regions,
                    "번호가 있는 표의 글과 본문을 고칠 수 있어요. 사진·글상자·머리말·중첩 표 등 번호가 없는 영역은 입력 화면이나 한글 앱에서 수정해 주세요.");
        }
    }

    public GeneratedDocument edit(UUID id, String token, DocumentEdits.Request request)
            throws IOException, HwpDocumentException {
        synchronized (store.require(id).directory()) {
            StoredDocument job = authorized(id, token);
            var current = regions(id, token);
            if (!current.revision().equals(request.revision())) throw new DocumentEdits.Conflict();
            var allowed = current.regions().stream().map(DocumentEdits.Region::id).toList();
            if (request.changes().stream().anyMatch(change -> !allowed.contains(change.id()))) {
                throw new DocumentEdits.Invalid("번호가 있는 영역을 선택해 주세요.");
            }
            Path draft = job.directory().resolve("edit-" + UUID.randomUUID() + ".hwp");
            Path rendering = job.directory().resolve("edit-preview");
            try {
                var editor = new HwpTextEditor(job.completedHwp());
                for (var change : request.changes()) editor.apply(change);
                editor.save(draft);
                var parsed = new HwpTextEditor(draft).texts();
                for (var change : request.changes()) {
                    if (!parsed.get(change.id()).stripTrailing().equals(change.text().stripTrailing())) {
                        throw new HwpDocumentException("수정한 글을 다시 읽어 확인하지 못했습니다.");
                    }
                }
                // Render before publishing: an invalid/unsupported edit keeps the prior HWP usable.
                previews.layout(draft, rendering);
                authorized(id, token); // expiration during rendering must not publish a new file
                PreviewService.invalidate(job);
                Files.deleteIfExists(job.completedPdf());
                Files.move(draft, job.completedHwp(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return new GeneratedDocument(job.completedFileName().hwp(), Files.readAllBytes(job.completedHwp()));
            } finally {
                Files.deleteIfExists(draft);
                PreviewService.deleteRendering(rendering);
            }
        }
    }

    private StoredDocument authorized(UUID id, String token) {
        StoredDocument job = store.require(id);
        job.requireEditToken(token);
        if (!Files.exists(job.completedHwp())) throw new DocumentStore.DocumentNotFoundException();
        return job;
    }

    private static String revision(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is required by Java", missing);
        }
    }
}
