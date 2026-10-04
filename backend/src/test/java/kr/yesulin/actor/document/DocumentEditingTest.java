package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
import kr.dogfoot.hwplib.writer.HWPWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class DocumentEditingTest {
    @TempDir Path work;

    @Test
    void editsMoveTextClearInstructionsAndKeepSourceWhenWorkingCopyIsEdited() throws Exception {
        // given: a private completed copy with a value in the wrong label cell
        Path source = Path.of(getClass().getResource("/forms/sample-notice.hwp").toURI());
        byte[] original = Files.readAllBytes(source);
        DocumentStore store = new DocumentStore(work, Duration.ofMinutes(30), Clock.systemUTC());
        StoredDocument job = store.create("지원서.hwp", original);
        HwpDocument hwp = HwpDocument.open(job.source());
        hwp.setText(new CellAddress(0, 7, 0), "지원동기");
        hwp.appendText(new CellAddress(0, 7, 0), "무대에 서고 싶습니다.");
        hwp.setText(new CellAddress(0, 8, 1), "빨간 안내는 삭제하고 제출");
        hwp.save(job.completedHwp());
        var colored = HWPReader.fromFile(job.completedHwp().toFile());
        var table = (ControlTable) ControlFinder.find(colored,
                (control, paragraph, section) -> control.getType() == ControlType.Table).getFirst();
        var note = table.getRowList().get(8).getCellList().get(1).getParagraphList().getParagraph(0);
        var red = colored.getDocInfo().getCharShapeList().get(Math.toIntExact(
                note.getCharShape().getPositonShapeIdPairList().getFirst().getShapeId())).clone();
        red.getCharColor().setValue(255);
        red.getProperty().setBold(true);
        colored.getDocInfo().getCharShapeList().add(red);
        note.getCharShape().getPositonShapeIdPairList().clear();
        note.getCharShape().addParaCharShape(0, colored.getDocInfo().getCharShapeList().size() - 1);
        HWPWriter.toFile(colored, job.completedHwp().toString());
        PdfConverter renderer = new PdfConverter(Path.of("../tools/rhwp/rhwp/rhwp.exe"), List.of());
        PreviewService previews = new PreviewService(store, renderer, JsonMapper.builder().build());
        DocumentEditingService editor = new DocumentEditingService(store, previews);
        var before = editor.regions(job.id(), job.editToken());
        assertThat(before.regions()).anySatisfy(region -> assertThat(region.id()).startsWith("p:"));
        assertThat(before.regions()).filteredOn(region -> region.id().equals("c:0.7.0"))
                .extracting(DocumentEdits.Region::text).containsExactly("지원동기\n무대에 서고 싶습니다.");
        Files.writeString(job.completedPdf(), "stale pdf");

        // when: one atomic batch leaves the label and replaces the instructions with the answer
        editor.edit(job.id(), job.editToken(), new DocumentEdits.Request(before.revision(), List.of(
                new DocumentEdits.Change("c:0.7.0", "지원동기", false),
                new DocumentEdits.Change("c:0.8.1", "무대에 서고 싶습니다.", true))));

        // then: independent reparse, original unchanged, preview/PDF refreshed and stale edits rejected
        var after = editor.regions(job.id(), job.editToken());
        assertThat(after.revision()).isNotEqualTo(before.revision());
        assertThat(after.regions()).filteredOn(region -> region.id().equals("c:0.7.0"))
                .extracting(DocumentEdits.Region::text).containsExactly("지원동기");
        assertThat(after.regions()).filteredOn(region -> region.id().equals("c:0.8.1"))
                .extracting(DocumentEdits.Region::text).containsExactly("무대에 서고 싶습니다.");
        assertThat(Files.readAllBytes(job.source())).isEqualTo(original);
        assertThat(Files.exists(job.completedPdf())).isFalse();
        var reparsed = HWPReader.fromFile(job.completedHwp().toFile());
        var changedTable = (ControlTable) ControlFinder.find(reparsed,
                (control, paragraph, section) -> control.getType() == ControlType.Table).getFirst();
        var value = changedTable.getRowList().get(8).getCellList().get(1).getParagraphList().getParagraph(0);
        for (var run : value.getCharShape().getPositonShapeIdPairList()) {
            var style = reparsed.getDocInfo().getCharShapeList().get(Math.toIntExact(run.getShapeId()));
            assertThat(style.getCharColor().getValue()).isZero();
            assertThat(style.getProperty().isBold()).isFalse();
        }
        editor.edit(job.id(), job.editToken(), new DocumentEdits.Request(after.revision(),
                List.of(new DocumentEdits.Change("p:0.0", "새 지원서 제목", false))));
        assertThatThrownBy(() -> editor.edit(job.id(), job.editToken(),
                new DocumentEdits.Request(before.revision(), List.of(new DocumentEdits.Change("c:0.0.1", "old", true)))))
                .isInstanceOf(DocumentEdits.Conflict.class);
        assertThatThrownBy(() -> editor.regions(job.id(), "wrong-token"))
                .isInstanceOf(DocumentEdits.Forbidden.class);
    }
}
