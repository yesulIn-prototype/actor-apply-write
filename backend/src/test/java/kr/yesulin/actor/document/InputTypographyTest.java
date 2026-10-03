package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.docinfo.CharShape;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
import kr.dogfoot.hwplib.writer.HWPWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InputTypographyTest {
    @TempDir
    Path work;

    @Test
    void setText_UsesOneFormTypeface_WhenInputCellsHaveDifferentFonts() throws Exception {
        CellAddress name = new CellAddress(0, 0, 1);
        CellAddress birthday = new CellAddress(0, 0, 3);
        Path source = withOutlierFont("application.hwp", name);
        HWPFile original = HWPReader.fromFile(source.toFile());
        assertThat(font(original, name)).isNotEqualTo(font(original, birthday));
        Path output = work.resolve("completed.hwp");

        HwpDocument document = HwpDocument.open(source);
        document.setText(name, "검증배우");
        document.setText(birthday, "1999년 1월 1일");
        document.save(output);

        HWPFile completed = HWPReader.fromFile(output.toFile());
        assertThat(font(completed, name)).isEqualTo(font(completed, birthday));
        assertThat(font(completed, name)).isNotEqualTo(font(original, name));
        assertThat(color(completed, name)).isZero();
        assertThat(color(completed, birthday)).isZero();
    }

    @Test
    void appendText_StylesOnlyNewValue_WhenLabelAlreadyExists() throws Exception {
        CellAddress name = new CellAddress(0, 0, 1);
        CellAddress birthday = new CellAddress(0, 1, 1);
        Path source = withOutlierFont("estc.hwp", name);
        HWPFile original = HWPReader.fromFile(source.toFile());
        int labelShape = firstShapeId(cell(original, name));
        Path output = work.resolve("appended.hwp");

        HwpDocument document = HwpDocument.open(source);
        document.appendText(name, "검증배우");
        document.setText(birthday, "1999년 1월 1일");
        document.save(output);

        HWPFile completed = HWPReader.fromFile(output.toFile());
        var paragraphs = cell(completed, name).getParagraphList();
        Paragraph paragraph = paragraphs.getParagraph(paragraphs.getParagraphCount() - 1);
        var runs = paragraph.getCharShape().getPositonShapeIdPairList();
        assertThat(firstShapeId(paragraphs.getParagraph(0))).isEqualTo(labelShape);
        int newShape = Math.toIntExact(runs.getLast().getShapeId());
        assertThat(completed.getDocInfo().getCharShapeList().get(newShape).getFaceNameIds().getHangul())
                .isEqualTo(font(completed, birthday));
        assertThat(completed.getDocInfo().getCharShapeList().get(newShape).getCharColor().getValue()).isZero();
        assertThat(HwpDocument.open(output).cells()).filteredOn(cell -> cell.address().equals(name))
                .extracting(CellSnapshot::text).anySatisfy(text -> assertThat(text).contains("성명(한글)", "검증배우"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"예시 입력 16", "예시 입력 16\n둘째 줄"})
    void appendText_UsesNormalWeightFromFirstCharacter_WhenLabelIsBold(String value) throws Exception {
        // given: a synthetic label and a paragraph terminator, as in an uploaded HWP
        CellAddress address = new CellAddress(0, 8, 1);
        Path fixture = Path.of(getClass().getResource("/forms/sample-notice.hwp").toURI());
        HWPFile source = HWPReader.fromFile(fixture.toFile());
        Paragraph label = cell(source, address).getParagraphList().getParagraph(0);
        label.createText();
        label.getText().addString("지원동기");
        CharShape bold = source.getDocInfo().getCharShapeList().get(firstShapeId(label)).clone();
        bold.getProperty().setBold(true);
        source.getDocInfo().getCharShapeList().add(bold);
        label.getCharShape().getPositonShapeIdPairList().clear();
        label.getCharShape().addParaCharShape(0, source.getDocInfo().getCharShapeList().size() - 1);
        Path input = work.resolve("bold-label.hwp");
        HWPWriter.toFile(source, input.toString());
        Path output = work.resolve("appended-bold-label.hwp");

        // when
        HwpDocument document = HwpDocument.open(input);
        document.appendText(address, value);
        document.save(output);

        // then: reparse and inspect the style effective on each glyph, not just the final style run
        HWPFile completed = HWPReader.fromFile(output.toFile());
        Paragraph paragraph = cell(completed, address).getParagraphList().getParagraph(0);
        assertThat(shapeAt(completed, paragraph, 0).getProperty().isBold()).isTrue();
        for (int position = "지원동기\n".length(); position < paragraph.getText().getCharSize() - 1; position++) {
            assertThat(shapeAt(completed, paragraph, position).getProperty().isBold())
                    .as("input weight at HWP character position %s", position).isFalse();
        }
        assertThat(paragraph.getText().getCharList().stream().filter(character -> character.getCode() == 0x0a))
                .hasSize(value.split("\n", -1).length);
        assertThat(HwpDocument.open(output).cells()).filteredOn(snapshot -> snapshot.address().equals(address))
                .extracting(CellSnapshot::text).containsExactly("지원동기" + value.replace("\n", ""));
    }

    private static CharShape shapeAt(HWPFile file, Paragraph paragraph, int position) {
        int shape = firstShapeId(paragraph);
        for (var run : paragraph.getCharShape().getPositonShapeIdPairList()) {
            if (run.getPosition() <= position) {
                shape = Math.toIntExact(run.getShapeId());
            }
        }
        return file.getDocInfo().getCharShapeList().get(shape);
    }

    private Path withOutlierFont(String fixture, CellAddress address) throws Exception {
        Path input = Path.of(getClass().getResource("/fixtures/" + fixture).toURI());
        HWPFile file = HWPReader.fromFile(input.toFile());
        int outlier = file.getDocInfo().getHangulFaceNameList().size();
        file.getDocInfo().addNewHangulFaceName().setName("서식검증용글꼴");
        CharShape original = file.getDocInfo().getCharShapeList().get(firstShapeId(cell(file, address)));
        CharShape changed = original.clone();
        changed.getFaceNameIds().setHangul(outlier);
        file.getDocInfo().getCharShapeList().add(changed);
        cell(file, address).getParagraphList().getParagraph(0).getCharShape()
                .getPositonShapeIdPairList().getFirst()
                .setShapeId(file.getDocInfo().getCharShapeList().size() - 1);
        Path source = work.resolve("source.hwp");
        HWPWriter.toFile(file, source.toString());
        return source;
    }

    private static Cell cell(HWPFile file, CellAddress address) {
        var tables = ControlFinder.find(file, (control, paragraph, section) -> control.getType() == ControlType.Table)
                .stream().map(ControlTable.class::cast).toList();
        return tables.get(address.tableIndex()).getRowList().get(address.rowIndex())
                .getCellList().get(address.cellIndex());
    }

    private static int firstShapeId(Cell cell) {
        return firstShapeId(cell.getParagraphList().getParagraph(0));
    }

    private static int firstShapeId(Paragraph paragraph) {
        return Math.toIntExact(paragraph.getCharShape()
                .getPositonShapeIdPairList().getFirst().getShapeId());
    }

    private static int font(HWPFile file, CellAddress address) {
        return file.getDocInfo().getCharShapeList().get(firstShapeId(cell(file, address)))
                .getFaceNameIds().getHangul();
    }

    private static long color(HWPFile file, CellAddress address) {
        return file.getDocInfo().getCharShapeList().get(firstShapeId(cell(file, address)))
                .getCharColor().getValue();
    }
}
