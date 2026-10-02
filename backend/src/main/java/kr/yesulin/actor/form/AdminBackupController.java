package kr.yesulin.actor.form;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Backing up and restoring every notice form (behind the admin token, like all of /api/admin). */
@RestController
@RequestMapping("/api/admin/backup")
public final class AdminBackupController {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final FormBackup backup;

    public AdminBackupController(FormBackup backup) {
        this.backup = backup;
    }

    @GetMapping(produces = "application/zip")
    public ResponseEntity<ByteArrayResource> download() throws IOException {
        byte[] zip = backup.write();
        String name = "yesulin-forms-" + LocalDateTime.now(SEOUL).format(STAMP) + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentLength(zip.length)
                .body(new ByteArrayResource(zip));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FormBackup.Restored restore(@RequestPart("backup") MultipartFile upload) throws IOException {
        try (var input = upload.getInputStream()) {
            return backup.restore(input);
        }
    }
}
