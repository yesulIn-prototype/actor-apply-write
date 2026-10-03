package kr.yesulin.actor.document;

import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public final class DocumentController {
    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");
    private final DocumentService service;
    private final PreviewService previews;

    public DocumentController(DocumentService service, PreviewService previews) {
        this.service = service;
        this.previews = previews;
    }

    @GetMapping("/{documentId}")
    public ResumeResponse resume(@PathVariable UUID documentId) {
        return service.resume(documentId);
    }

    @GetMapping(path = "/{documentId}/completed", produces = "application/x-hwp")
    public ResponseEntity<ByteArrayResource> completed(@PathVariable UUID documentId) throws IOException {
        return attachment(service.completed(documentId));
    }

    @GetMapping(path = "/{documentId}/completed.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> completedPdf(@PathVariable UUID documentId)
            throws IOException, HwpDocumentException {
        GeneratedDocument pdf = service.completedPdf(documentId);
        return attachment(pdf, MediaType.APPLICATION_PDF);
    }

    @GetMapping("/{documentId}/preview")
    public PreviewResponse preview(@PathVariable UUID documentId) throws IOException, HwpDocumentException {
        return previews.preview(documentId);
    }

    @GetMapping("/{documentId}/preview/{page}")
    public ResponseEntity<ByteArrayResource> previewPage(@PathVariable UUID documentId, @PathVariable int page)
            throws IOException, HwpDocumentException {
        byte[] svg = previews.page(documentId, page);
        return ResponseEntity.ok().contentType(SVG).contentLength(svg.length).body(new ByteArrayResource(svg));
    }

    private static ResponseEntity<ByteArrayResource> attachment(GeneratedDocument generated) {
        return Attachments.of(generated, Attachments.HWP);
    }

    private static ResponseEntity<ByteArrayResource> attachment(GeneratedDocument generated, MediaType type) {
        return Attachments.of(generated, type);
    }
}
