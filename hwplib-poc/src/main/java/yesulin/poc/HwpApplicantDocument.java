package yesulin.poc;

import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharType;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
import kr.dogfoot.hwplib.writer.HWPWriter;

public final class HwpApplicantDocument {
    private final HWPFile file;
    private final List<ControlTable> tables;

    private HwpApplicantDocument(HWPFile file, List<ControlTable> tables) {
        this.file = file;
        this.tables = List.copyOf(tables);
    }

    public static HwpApplicantDocument open(Path path) throws Exception {
        HWPFile file = HWPReader.fromFile(path.toFile());
        if (file == null) {
            throw new HwpDocumentReadException(path);
        }

        ArrayList<Control> controls = ControlFinder.find(
                file, (control, paragraph, section) -> control.getType() == ControlType.Table);
        List<ControlTable> tables = controls.stream()
                .map(ControlTable.class::cast)
                .toList();
        return new HwpApplicantDocument(file, tables);
    }

    public int tableCount() {
        return tables.size();
    }

    public int rowCount(int tableIndex) {
        return table(tableIndex).getRowList().size();
    }

    public int cellCount(int tableIndex, int rowIndex) {
        return row(tableIndex, rowIndex).getCellList().size();
    }

    public String cellText(int tableIndex, int rowIndex, int cellIndex)
            throws UnsupportedEncodingException {
        return cell(tableIndex, rowIndex, cellIndex)
                .getParagraphList()
                .getNormalString()
                .strip();
    }

    public void setCellText(int tableIndex, int rowIndex, int cellIndex, String value)
            throws UnsupportedEncodingException {
        if (value == null) {
            throw new IllegalArgumentException("Cell value must not be null");
        }

        Paragraph paragraph = firstParagraph(cell(tableIndex, rowIndex, cellIndex));
        ParaText text = paragraph.getText();
        if (text == null) {
            paragraph.createText();
            text = paragraph.getText();
        }
        text.getCharList().removeIf(character ->
                character.getType() == HWPCharType.Normal && character.getCode() != 0x0d);
        text.addString(value);
        paragraph.deleteLineSeg();
    }

    public int embeddedImageCount() {
        return file.getBinData().getEmbeddedBinaryDataList().size();
    }

    public void insertImageInCell(int tableIndex, int rowIndex, int cellIndex, Path image)
            throws Exception {
        CellImageInserter.insert(file, cell(tableIndex, rowIndex, cellIndex), image);
    }

    public void save(Path output) throws Exception {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        HWPWriter.toFile(file, output.toString());
    }

    public List<CellSnapshot> cellSnapshots() throws UnsupportedEncodingException {
        List<CellSnapshot> snapshots = new ArrayList<>();
        for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
            ControlTable table = tables.get(tableIndex);
            for (int rowIndex = 0; rowIndex < table.getRowList().size(); rowIndex++) {
                Row row = table.getRowList().get(rowIndex);
                for (int cellIndex = 0; cellIndex < row.getCellList().size(); cellIndex++) {
                    Cell cell = row.getCellList().get(cellIndex);
                    snapshots.add(new CellSnapshot(
                            tableIndex,
                            rowIndex,
                            cellIndex,
                            cell.getListHeader().getColIndex(),
                            cell.getListHeader().getRowIndex(),
                            cell.getListHeader().getColSpan(),
                            cell.getListHeader().getRowSpan(),
                            cell.getListHeader().getWidth(),
                            cell.getListHeader().getHeight(),
                            cell.getParagraphList().getNormalString().strip()));
                }
            }
        }
        return List.copyOf(snapshots);
    }

    private ControlTable table(int tableIndex) {
        return tables.get(tableIndex);
    }

    private Row row(int tableIndex, int rowIndex) {
        return table(tableIndex).getRowList().get(rowIndex);
    }

    private Cell cell(int tableIndex, int rowIndex, int cellIndex) {
        return row(tableIndex, rowIndex).getCellList().get(cellIndex);
    }

    private static Paragraph firstParagraph(Cell cell) {
        if (cell.getParagraphList().getParagraphCount() == 0) {
            return cell.getParagraphList().addNewParagraph();
        }
        return cell.getParagraphList().getParagraph(0);
    }

    public record CellSnapshot(
            int tableIndex,
            int rowIndex,
            int cellIndex,
            int columnAddress,
            int rowAddress,
            int columnSpan,
            int rowSpan,
            long width,
            long height,
            String text) {}

    public static final class HwpDocumentReadException extends Exception {
        private final Path path;

        private HwpDocumentReadException(Path path) {
            super("Unable to read HWP document: " + path);
            this.path = path;
        }

        public Path path() {
            return path;
        }
    }
}
