package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
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

class InputTypographyTest {
    @TempDir
    Path work;

    @Test
    void setText_UsesOneFormTypeface_WhenInputCellsHaveDifferentFonts() throws Exception {
        Path fixture = Path.of(getClass().getResource("/fixtures/application.hwp").toURI());
        List<FieldCandidate> fields = new FieldExtractor().extract(HwpDocument.open(fixture).cells());
        CellAddress name = address(fields, "이름");
        CellAddress birthday = address(fields, "생년월일");
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

    private static CellAddress address(List<FieldCandidate> fields, String label) {
        return fields.stream().filter(field -> field.label().equals(label)).findFirst().orElseThrow().address();
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
