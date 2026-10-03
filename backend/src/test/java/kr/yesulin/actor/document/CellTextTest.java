package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CellTextTest {
    @TempDir
    Path work;

    @Test
    void setText_ReplacesGuideText_WhenValueHasMultipleLines() throws Exception {
        // given
        HwpDocument document = HwpDocument.open(fixture("application.hwp"));
        CellAddress career = new CellAddress(0, 10, 0);
        Path output = work.resolve("multiline.hwp");

        // when
        document.setText(career, "햄릿 / 햄릿 / 대학로 / 2025\n갈매기 / 트레플레프 / 아르코 / 2024");
        document.save(output);

        // then
        assertThat(cellText(output, career))
                .contains("햄릿 / 햄릿 / 대학로 / 2025", "갈매기 / 트레플레프 / 아르코 / 2024")
                .doesNotContain("학교작품");
    }

    @Test
    void appendText_KeepsLabelAndTemplateParagraphs_WhenValueIsInserted() throws Exception {
        // given
        HwpDocument document = HwpDocument.open(fixture("estc.hwp"));
        CellAddress name = new CellAddress(0, 0, 1);
        CellAddress language = new CellAddress(0, 8, 1);
        Path output = work.resolve("append.hwp");

        // when
        document.appendText(name, "홍길동");
        document.setText(language, "외국어 1: ( 영어 ) 독해 -- 상\n외국어 2: ( 일본어 ) 독해 -- 중");
        document.save(output);

        // then
        assertThat(cellText(output, name)).contains("성명(한글)", "홍길동");
        assertThat(cellText(output, language)).contains("외국어 1: ( 영어 )", "외국어 2: ( 일본어 )");
    }

    private static String cellText(Path hwp, CellAddress address) throws HwpDocumentException {
        return HwpDocument.open(hwp).cells().stream().filter(cell -> cell.address().equals(address))
                .findFirst().orElseThrow().text();
    }

    private Path fixture(String name) throws Exception {
        return Path.of(getClass().getResource("/fixtures/" + name).toURI());
    }
}
