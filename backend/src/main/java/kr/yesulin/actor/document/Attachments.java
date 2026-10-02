package kr.yesulin.actor.document;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A completed file as a download, under its chosen name (UTF-8, so Korean names survive). */
public final class Attachments {
    public static final MediaType HWP = MediaType.parseMediaType("application/x-hwp");

    private Attachments() {}

    public static ResponseEntity.BodyBuilder download(GeneratedDocument generated, MediaType type) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(generated.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(generated.content().length);
    }

    public static ResponseEntity<ByteArrayResource> of(GeneratedDocument generated, MediaType type) {
        return download(generated, type).body(new ByteArrayResource(generated.content()));
    }
}
