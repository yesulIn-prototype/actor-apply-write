package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Photos must survive the PDF conversion. rhwp dropped them from older forms (ESTC, 하츄핑)
 * until photos were written as picture objects with their own line layout.
 */
class PhotoPdfTest {
    private static final Pattern PDF_IMAGE = Pattern.compile("/Subtype\\s*/Image");
    private final PdfConverter converter = new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp"), List.of());

    @TempDir
    Path directory;

    @ParameterizedTest(name = "{0}")
    @DisplayName("사진을 넣은 칸이 PDF에도 그림으로 나온다")
    @CsvSource({
        "application.hwp, 1, 0, 0",
        "estc.hwp, 0, 0, 4",
        "hachu.hwp, 0, 1, 0",
        "life.hwp, 0, 4, 0",
    })
    void photoAppearsInPdf(String form, int table, int row, int cell) throws Exception {
        assumeTrue(converter.available(), "rhwp not installed — run tools/install-rhwp.sh");
        HwpDocument document = HwpDocument.open(fixture(form));
        document.insertImage(new CellAddress(table, row, cell), fixture("portrait.jpg"));
        Path hwp = directory.resolve("photo.hwp");
        Path pdf = directory.resolve("photo.pdf");
        document.save(hwp);

        converter.convert(hwp, pdf);

        String content = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
        assertThat(PDF_IMAGE.matcher(content).find()).as("image in %s PDF", form).isTrue();
    }

    private static Path fixture(String name) throws Exception {
        return Path.of(PhotoPdfTest.class.getResource("/fixtures/" + name).toURI());
    }
}
