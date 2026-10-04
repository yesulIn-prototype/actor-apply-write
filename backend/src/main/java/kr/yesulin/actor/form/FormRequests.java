package kr.yesulin.actor.form;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.yesulin.actor.document.Attachments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** Shared HTTP plumbing of the applicant and operator form APIs. */
final class FormRequests {
    /** Lets the page keep rebuilding the same applicant's file instead of starting a new one. */
    static final String DOCUMENT_ID = "X-Document-Id";
    private static final String PHOTO_PREFIX = "photo-";

    private FormRequests() {}

    /** Photo parts are named "photo-<item id>". */
    static Map<String, MultipartFile> photos(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> photos = new LinkedHashMap<>();
        request.getFileMap().forEach((key, file) -> {
            if (key.startsWith(PHOTO_PREFIX)) {
                photos.put(key.substring(PHOTO_PREFIX.length()), file);
            }
        });
        return photos;
    }

    static ResponseEntity<ByteArrayResource> built(FormBuilder.Built built) {
        return Attachments.download(built.document(), Attachments.HWP)
                .header(DOCUMENT_ID, built.documentId().toString())
                .header(kr.yesulin.actor.document.DocumentEdits.TOKEN_HEADER, built.editToken())
                .body(new ByteArrayResource(built.document().content()));
    }
}
