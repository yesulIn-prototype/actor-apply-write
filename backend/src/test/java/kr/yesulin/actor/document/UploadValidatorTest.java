package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import kr.yesulin.actor.document.UploadValidator.Format;
import kr.yesulin.actor.document.UploadValidator.InvalidUploadException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadValidatorTest {
    private static final byte[] OLE = {
        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };

    @Test
    void acceptsOnlyHwpAndHwpxNames() throws Exception {
        byte[] hwp = fixture("application.hwp");

        assertThat(UploadValidator.hangul(upload("지원서.hwp", hwp)).format()).isEqualTo(Format.HWP);
        assertThat(UploadValidator.hangul(upload("지원서.HWP", hwp)).format()).isEqualTo(Format.HWP);
        assertThat(UploadValidator.hangul(upload("지원서.hwpx", fixture("hangang.hwpx"))).format())
                .isEqualTo(Format.HWPX);
        for (String name : new String[] {"지원서.pdf", "지원서.hwp.exe", "지원서.docx", "지원서", "hwp"}) {
            assertThatThrownBy(() -> UploadValidator.hangul(upload(name, hwp)))
                    .as(name).isInstanceOf(InvalidUploadException.class);
        }
    }

    @Test
    void rejectsContentThatIsNotAHangulDocument() throws Exception {
        // A renamed PDF, another OLE file (.doc/.xls), and a zip that is not an HWPX package.
        assertThatThrownBy(() -> UploadValidator.hangul(upload("a.hwp", "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(InvalidUploadException.class);
        byte[] word = new byte[4096];
        System.arraycopy(OLE, 0, word, 0, OLE.length);
        assertThatThrownBy(() -> UploadValidator.hangul(upload("a.hwp", word))).isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> UploadValidator.hangul(upload("a.hwpx", zip("mimetype", "application/zip"))))
                .isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> UploadValidator.hangul(upload("a.hwpx", fixture("hangang.hwpx"), 100)))
                .as("truncated package").isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> UploadValidator.hangul(upload("a.hwp", new byte[0])))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void rejectsZipBombs() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("application/hwp+zip".getBytes(StandardCharsets.US_ASCII));
            zip.putNextEntry(new ZipEntry("Contents/section0.xml"));
            byte[] zeros = new byte[1024 * 1024];
            for (int megabyte = 0; megabyte < 210; megabyte++) {
                zip.write(zeros);
            }
        }
        assertThat(bytes.size()).as("compresses to under a megabyte").isLessThan(1024 * 1024);

        assertThatThrownBy(() -> UploadValidator.hangul(upload("bomb.hwpx", bytes.toByteArray())))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void checksPhotosByContentAndPixelSize() throws Exception {
        assertThat(UploadValidator.image(photo("portrait.png", png(1200, 1600)))).isEqualTo(".png");
        assertThat(UploadValidator.image(photo("renamed.png", fixture("portrait.jpg")))).as("content decides").isEqualTo(".jpg");
        assertThatThrownBy(() -> UploadValidator.image(photo("huge.png", png(9000, 10))))
                .isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> UploadValidator.image(photo("fake.jpg", "<svg/>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(InvalidUploadException.class);
    }

    private static MockMultipartFile upload(String name, byte[] bytes) {
        return new MockMultipartFile("document", name, "application/octet-stream", bytes);
    }

    private static MockMultipartFile upload(String name, byte[] bytes, int length) {
        return upload(name, java.util.Arrays.copyOf(bytes, length));
    }

    private static MockMultipartFile photo(String name, byte[] bytes) {
        return new MockMultipartFile("photo", name, "image/png", bytes);
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.US_ASCII));
        }
        return bytes.toByteArray();
    }

    private static byte[] fixture(String name) throws Exception {
        try (var input = UploadValidatorTest.class.getResourceAsStream("/fixtures/" + name)) {
            return input.readAllBytes();
        }
    }
}
