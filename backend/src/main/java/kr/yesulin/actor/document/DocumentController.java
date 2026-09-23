package kr.yesulin.actor.document;

import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/documents")
public final class DocumentController {
    private static final MediaType HWP_MEDIA_TYPE = MediaType.parseMediaType("application/x-hwp");
    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");
    private final DocumentService service;
    private final PreviewService previews;

    public DocumentController(DocumentService service, PreviewService previews) {
        this.service = service;
        this.previews = previews;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@RequestPart("document") MultipartFile document)
            throws IOException, HwpDocumentException {
        return service.analyze(document);
    }

    @PostMapping(
            path = "/{documentId}/generate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/x-hwp")
    public ResponseEntity<ByteArrayResource> generate(
            @PathVariable UUID documentId,
            @Valid @RequestPart("request") GenerateRequest request,
            MultipartHttpServletRequest multipartRequest) throws IOException, HwpDocumentException {
        Map<String, MultipartFile> photos = new LinkedHashMap<>();
        multipartRequest.getFileMap().forEach((key, value) -> {
            if (!key.equals("request")) {
                photos.put(key, value);
            }
        });
        return attachment(service.generate(documentId, request, photos));
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
        return attachment(generated, HWP_MEDIA_TYPE);
    }

    private static ResponseEntity<ByteArrayResource> attachment(GeneratedDocument generated, MediaType type) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(generated.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(generated.content().length)
                .body(new ByteArrayResource(generated.content()));
    }
}
