package kr.yesulin.actor.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checks uploads by name and by content before anything parses them: only .hwp / .hwpx names, and the
 * bytes must really be a Hangul document (or a JPEG/PNG photo of sane pixel size).
 */
public final class UploadValidator {
    static final long MAX_HWP_BYTES = 20L * 1024L * 1024L;
    static final long MAX_IMAGE_BYTES = 12L * 1024L * 1024L;
    /** Rendering decodes every photo; a 30 000 px image would exhaust the renderer's memory. */
    static final int MAX_IMAGE_SIDE = 8_000;
    static final long MAX_IMAGE_PIXELS = 40_000_000L;
    /** A form unpacks to a few MB; far more means a zip bomb. */
    private static final long MAX_HWPX_UNPACKED_BYTES = 200L * 1024L * 1024L;
    private static final int MAX_HWPX_ENTRIES = 5_000;
    private static final byte[] OLE_SIGNATURE = {
        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };
    /** Start of every HWP 5 FileHeader stream; other OLE files (.doc, .xls) lack it. */
    private static final byte[] HWP_FILE_HEADER = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4b, 0x03, 0x04};
    private static final String HWPX_MIMETYPE = "application/hwp+zip";

    public enum Format { HWP, HWPX }

    public record HangulUpload(Format format, byte[] bytes) {}

    private UploadValidator() {}

    static HangulUpload hangul(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().strip().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".hwp") && !name.endsWith(".hwpx")) {
            throw new InvalidUploadException(".hwp 또는 .hwpx 파일만 올릴 수 있습니다.");
        }
        byte[] bytes = limitedBytes(file, MAX_HWP_BYTES, "한글 파일은 20MB 이하여야 합니다.");
        if (startsWith(bytes, OLE_SIGNATURE)) {
            if (indexOf(bytes, HWP_FILE_HEADER) < 0) {
                throw new InvalidUploadException("한글(HWP) 문서가 아닙니다.");
            }
            return new HangulUpload(Format.HWP, bytes);
        }
        if (startsWith(bytes, ZIP_SIGNATURE)) {
            checkHwpx(bytes);
            return new HangulUpload(Format.HWPX, bytes);
        }
        throw new InvalidUploadException("정상적인 한글 문서가 아닙니다.");
    }

    /** The first entry of an HWPX package is "mimetype" holding application/hwp+zip (OWPML). */
    private static void checkHwpx(byte[] bytes) throws IOException {
        long unpacked = 0;
        int entries = 0;
        boolean sawSection = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry first = zip.getNextEntry();
            if (first == null || !first.getName().equals("mimetype")
                    || !new String(zip.readNBytes(64), StandardCharsets.US_ASCII).strip().equals(HWPX_MIMETYPE)) {
                throw new InvalidUploadException("한글(HWPX) 문서가 아닙니다.");
            }
            byte[] buffer = new byte[64 * 1024];
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_HWPX_ENTRIES) {
                    throw new InvalidUploadException("HWPX 파일 구성이 비정상입니다.");
                }
                String entryName = entry.getName();
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\")) {
                    throw new InvalidUploadException("HWPX 파일 구성이 비정상입니다.");
                }
                sawSection |= entryName.startsWith("Contents/section");
                for (int read = zip.read(buffer); read > 0; read = zip.read(buffer)) {
                    unpacked += read;
                    if (unpacked > MAX_HWPX_UNPACKED_BYTES) {
                        throw new InvalidUploadException("HWPX 파일 구성이 비정상입니다.");
                    }
                }
            }
        } catch (java.util.zip.ZipException exception) {
            throw new InvalidUploadException("손상된 HWPX 파일입니다.");
        }
        if (!sawSection) {
            throw new InvalidUploadException("한글(HWPX) 문서가 아닙니다.");
        }
    }

    /** Returns the file extension matching the photo's real content: ".jpg" or ".png". */
    static String image(MultipartFile file) throws IOException {
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
        int[] size = pixelSize(bytes);
        if (size == null) {
            throw new InvalidUploadException("사진을 읽을 수 없습니다.");
        }
        if (size[0] > MAX_IMAGE_SIDE || size[1] > MAX_IMAGE_SIDE || (long) size[0] * size[1] > MAX_IMAGE_PIXELS) {
            throw new InvalidUploadException("사진 해상도가 너무 큽니다.");
        }
        return png ? ".png" : ".jpg";
    }

    /** Width and height from the header only; the pixels are never decoded here. */
    private static int[] pixelSize(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } catch (IOException exception) {
                return null;
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] limitedBytes(MultipartFile file, long max, String message) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidUploadException("빈 파일은 사용할 수 없습니다.");
        }
        if (file.getSize() > max) {
            throw new InvalidUploadException(message);
        }
        return file.getBytes();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }

    private static int indexOf(byte[] bytes, byte[] needle) {
        outer:
        for (int start = 0; start <= bytes.length - needle.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (bytes[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }

    public static final class InvalidUploadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidUploadException(String message) {
            super(message);
        }
    }
}
