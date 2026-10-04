package kr.yesulin.actor.document;

import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/editing")
public final class DocumentEditingController {
    private final DocumentEditingService service;
    public DocumentEditingController(DocumentEditingService service) { this.service = service; }

    @GetMapping
    public DocumentEdits.View regions(@PathVariable UUID documentId,
            @RequestHeader(value = DocumentEdits.TOKEN_HEADER, required = false) String token)
            throws IOException, HwpDocumentException {
        return service.regions(documentId, token);
    }

    @PostMapping
    public ResponseEntity<ByteArrayResource> edit(@PathVariable UUID documentId,
            @RequestHeader(value = DocumentEdits.TOKEN_HEADER, required = false) String token,
            @RequestBody DocumentEdits.Request request) throws IOException, HwpDocumentException {
        return Attachments.of(service.edit(documentId, token, request), Attachments.HWP);
    }
}
