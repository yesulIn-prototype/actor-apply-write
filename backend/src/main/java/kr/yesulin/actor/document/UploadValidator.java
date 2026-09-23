package kr.yesulin.actor.document;

import java.util.Arrays;
import org.springframework.web.multipart.MultipartFile;

public final class UploadValidator {
    static final long MAX_HWP_BYTES = 20L * 1024L * 1024L;
    static final long MAX_IMAGE_BYTES = 12L * 1024L * 1024L;
    private static final byte[] HWP_SIGNATURE = {
        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };

    private UploadValidator() {}

    static byte[] hwp(MultipartFile file) throws java.io.IOException {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".hwp")) {
            throw new InvalidUploadException(".hwp 파일만 업로드할 수 있습니다.");
        }
        byte[] bytes = limitedBytes(file, MAX_HWP_BYTES, "HWP 파일은 20MB 이하여야 합니다.");
        if (bytes.length < HWP_SIGNATURE.length
                || !Arrays.equals(Arrays.copyOf(bytes, HWP_SIGNATURE.length), HWP_SIGNATURE)) {
            throw new InvalidUploadException("정상적인 HWP 5.x 파일이 아닙니다.");
        }
        return bytes;
    }

    static void image(MultipartFile file) throws java.io.IOException {
        byte[] bytes = limitedBytes(file, MAX_IMAGE_BYTES, "사진은 장당 12MB 이하여야 합니다.");
        boolean jpeg = bytes.length >= 3
                && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        boolean png = bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                && bytes[6] == 0x1a && bytes[7] == 0x0a;
        if (!jpeg && !png) {
            throw new InvalidUploadException("사진은 JPEG 또는 PNG 파일이어야 합니다.");
        }
    }

    private static byte[] limitedBytes(MultipartFile file, long max, String message)
            throws java.io.IOException {
        if (file.isEmpty()) {
            throw new InvalidUploadException("빈 파일은 사용할 수 없습니다.");
        }
        if (file.getSize() > max) {
            throw new InvalidUploadException(message);
        }
        return file.getBytes();
    }

    public static final class InvalidUploadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidUploadException(String message) {
            super(message);
        }
    }
}
