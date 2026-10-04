package kr.yesulin.actor.document;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharType;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
import kr.dogfoot.hwplib.writer.HWPWriter;

/** Thread-confined text edits. Paragraphs containing drawings/structural controls are excluded. */
final class HwpTextEditor {
    private final HWPFile file;
    private final Map<String, List<Paragraph>> targets = new LinkedHashMap<>();
    private final Map<String, Integer> sizes = new LinkedHashMap<>();
    private final int representative;

    HwpTextEditor(Path path) throws HwpDocumentException {
        try {
            file = HWPReader.fromFile(path.toFile());
            if (file == null) throw new HwpDocumentException("문서를 읽지 못했습니다.");
            var tables = ControlFinder.find(file, (control, paragraph, section) -> control.getType() == ControlType.Table)
                    .stream().map(ControlTable.class::cast).toList();
            var sections = file.getBodyText().getSectionList();
            int topLevelTables = 0;
            for (var section : sections) {
                for (var paragraph : section) {
                    if (paragraph.getControlList() != null) {
                        topLevelTables += (int) paragraph.getControlList().stream()
                                .filter(control -> control.getType() == ControlType.Table).count();
                    }
                }
            }
            if (sections.size() != 1 || topLevelTables != tables.size()) {
                throw new DocumentEdits.Invalid("중첩 표·여러 구역·글상자 안 표가 있는 문서는 위치를 정확히 연결할 수 없어 직접 수정을 제공하지 않아요. 한글 앱에서 수정해 주세요.");
            }
            var cells = tables.stream().flatMap(table -> table.getRowList().stream())
                    .flatMap(row -> row.getCellList().stream()).toList();
            representative = CellContent.representativeShapeId(file, cells);
            for (int t = 0; t < tables.size(); t++) {
                var table = tables.get(t);
                int size = CellContent.typicalSize(file, table.getRowList().stream()
                        .flatMap(row -> row.getCellList().stream()).toList());
                for (int r = 0; r < table.getRowList().size(); r++) {
                    var row = table.getRowList().get(r);
                    for (int c = 0; c < row.getCellList().size(); c++) {
                        List<Paragraph> paragraphs = new ArrayList<>();
                        row.getCellList().get(c).getParagraphList().forEach(paragraphs::add);
                        add("c:" + t + "." + r + "." + c, paragraphs, size);
                    }
                }
            }
            // rhwp's TextLine pi has no section id. Outside text can be mapped safely for one section only.
            if (sections.size() == 1) {
                var section = sections.getFirst();
                for (int p = 0; p < section.getParagraphCount(); p++) {
                    add("p:0." + p, List.of(section.getParagraph(p)), 0);
                }
            }
        } catch (HwpDocumentException exception) {
            throw exception;
        } catch (DocumentEdits.Invalid exception) {
            throw exception;
        } catch (Exception exception) { // no-excuse-ok: catch - hwplib boundary
            throw new HwpDocumentException("문서의 편집 영역을 읽지 못했습니다.", exception);
        }
    }

    private void add(String id, List<Paragraph> paragraphs, int size) {
        if (paragraphs.isEmpty() || paragraphs.stream().anyMatch(paragraph ->
                paragraph.getControlList() != null && paragraph.getControlList().stream().anyMatch(control ->
                        control.getType() != ControlType.SectionDefine && control.getType() != ControlType.ColumnDefine))) return;
        targets.put(id, List.copyOf(paragraphs));
        sizes.put(id, size);
    }

    Map<String, String> texts() {
        Map<String, String> result = new LinkedHashMap<>();
        targets.forEach((id, paragraphs) -> result.put(id, String.join("\n", paragraphs.stream()
                .map(HwpTextEditor::text).toList())));
        return Map.copyOf(result);
    }

    void apply(DocumentEdits.Change change) throws HwpDocumentException {
        List<Paragraph> paragraphs = targets.get(change.id());
        if (paragraphs == null) throw new DocumentEdits.Invalid("이 영역은 직접 수정할 수 없습니다.");
        String[] lines = change.text().split("\n", -1);
        try {
            for (int i = 0; i < paragraphs.size(); i++) {
                Paragraph paragraph = paragraphs.get(i);
                String value = i < lines.length ? lines[i] : "";
                if (i == paragraphs.size() - 1 && lines.length > paragraphs.size()) {
                    value = String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length));
                }
                if (paragraph.getText() != null) {
                    paragraph.getText().getCharList().removeIf(character ->
                            (character.getType() == HWPCharType.Normal && character.getCode() != 0x0d)
                                    || character.isLineBreak());
                }
                var text = CellContent.text(paragraph);
                String[] parts = value.split("\n", -1);
                for (int line = 0; line < parts.length; line++) {
                    if (line > 0) text.addNewCharControlChar().setCode(0x0a);
                    text.addString(parts[line]);
                }
                if (change.inputStyle()) {
                    CellContent.useInputStyle(file, paragraph, representative, sizes.get(change.id()));
                } else if (paragraph.getCharShape() != null) {
                    // Keep label/title styles and trim obsolete runs (e.g. a removed appended answer).
                    int length = text.getCharSize();
                    paragraph.getCharShape().getPositonShapeIdPairList().removeIf(run -> run.getPosition() >= length);
                }
                paragraph.deleteLineSeg();
            }
        } catch (Exception exception) { // no-excuse-ok: catch - hwplib mutation boundary
            throw new HwpDocumentException("문서의 글을 수정하지 못했습니다.", exception);
        }
    }

    void save(Path path) throws HwpDocumentException {
        try {
            HWPWriter.toFile(file, path.toString());
        } catch (Exception exception) { // no-excuse-ok: catch - hwplib writer boundary
            throw new HwpDocumentException("수정한 문서를 저장하지 못했습니다.", exception);
        }
    }

    private static String text(Paragraph paragraph) {
        StringBuilder result = new StringBuilder();
        if (paragraph.getText() == null) return "";
        for (var character : paragraph.getText().getCharList()) {
            if (character.isLineBreak()) result.append('\n');
            else if (character.getType() == HWPCharType.Normal && character.getCode() != 0x0d) {
                result.append((char) character.getCode());
            }
        }
        return result.toString();
    }
}
