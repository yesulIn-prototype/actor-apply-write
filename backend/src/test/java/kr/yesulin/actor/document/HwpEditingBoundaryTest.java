package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
import kr.dogfoot.hwplib.writer.HWPWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HwpEditingBoundaryTest {
    @TempDir Path work;

    @Test
    void refusesNestedTablesWhenAnotherTopLevelTableHasTheSameDimensions() throws Exception {
        // given: outer table, nested table, then an independent equal-sized outer table
        var file = HWPReader.fromFile(fixture().toFile());
        var section = file.getBodyText().getSectionList().getFirst();
        var tableParagraph = section.getParagraph(2).clone();
        var nested = tableParagraph.clone();
        var outer = (ControlTable) section.getParagraph(2).getControlList().getFirst();
        outer.getRowList().get(3).getCellList().get(1).getParagraphList().addParagraph(nested);
        section.insertParagraph(section.getParagraphCount(), tableParagraph);
        Path input = work.resolve("nested.hwp");
        HWPWriter.toFile(file, input.toString());
        assertThat(ControlFinder.find(HWPReader.fromFile(input.toFile()), (control, paragraph, owner) ->
                control.getType() == ControlType.Table)).hasSize(3);
        // when / then: refuse the whole direct-edit mapping rather than selecting another cell silently
        assertThatThrownBy(() -> new HwpTextEditor(input)).isInstanceOf(DocumentEdits.Invalid.class)
                .hasMessageContaining("중첩 표");
    }

    @Test
    void keepsPhotoBinaryAndMergedCellLayoutWhenOtherTextIsEdited() throws Exception {
        // given: a synthetic drawn photo, not a real applicant's image
        Path picture = work.resolve("drawn.png");
        var image = new BufferedImage(80, 100, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 80, 100);
        graphics.setColor(Color.BLUE); graphics.fillOval(20, 10, 40, 40); graphics.fillRect(15, 55, 50, 45);
        graphics.dispose(); ImageIO.write(image, "png", picture.toFile());
        Path before = work.resolve("photo.hwp");
        var document = HwpDocument.open(fixture());
        document.insertImage(new CellAddress(0, 0, 4), picture);
        document.save(before);
        var old = HWPReader.fromFile(before.toFile());
        var oldCells = HwpDocument.open(before).cells();
        byte[] photo = old.getBinData().getEmbeddedBinaryDataList().getFirst().getData();
        var editor = new HwpTextEditor(before);
        assertThat(editor.texts()).doesNotContainKey("c:0.0.4");
        // when
        editor.apply(new DocumentEdits.Change("c:0.0.1", "수정배우", true));
        Path after = work.resolve("edited-photo.hwp"); editor.save(after);
        // then: independent HWP parser proves binary and every span unchanged
        var saved = HWPReader.fromFile(after.toFile());
        assertThat(saved.getBinData().getEmbeddedBinaryDataList().getFirst().getData()).isEqualTo(photo);
        assertThat(HwpDocument.open(after).cells()).extracting(CellSnapshot::columnSpan, CellSnapshot::rowSpan)
                .containsExactlyElementsOf(oldCells.stream().map(cell -> org.assertj.core.groups.Tuple.tuple(cell.columnSpan(), cell.rowSpan())).toList());
    }

    private Path fixture() throws Exception { return Path.of(getClass().getResource("/forms/sample-notice.hwp").toURI()); }
}
