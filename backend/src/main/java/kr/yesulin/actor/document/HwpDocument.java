package kr.yesulin.actor.document;

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
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.ParaText;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.objectfinder.ControlFinder;
import kr.dogfoot.hwplib.writer.HWPWriter;

public final class HwpDocument {
    private static final int LINE_BREAK = 0x0a;
    private final HWPFile file;
    private final List<ControlTable> tables;
    private final int representativeShapeId;
    private final java.util.Map<Integer, Integer> typicalSizes = new java.util.HashMap<>();

    private HwpDocument(HWPFile file, List<ControlTable> tables) {
        this.file = file;
        this.tables = List.copyOf(tables);
        this.representativeShapeId = CellContent.representativeShapeId(file, allCells());
    }

    public static HwpDocument open(Path path) throws HwpDocumentException {
        try {
            HWPFile file = HWPReader.fromFile(path.toFile());
            if (file == null) {
                throw new HwpDocumentException("HWP 문서를 읽을 수 없습니다.");
            }
            ArrayList<Control> controls = ControlFinder.find(
                    file, (control, paragraph, section) -> control.getType() == ControlType.Table);
            List<ControlTable> tables = controls.stream().map(ControlTable.class::cast).toList();
            return new HwpDocument(file, tables);
        } catch (HwpDocumentException exception) {
            throw exception;
        } catch (Exception exception) { // no-excuse-ok: catch - translate hwplib reader failures at this boundary
            throw new HwpDocumentException("HWP 문서를 해석하지 못했습니다.", exception);
        }
    }

    public List<CellSnapshot> cells() throws HwpDocumentException {
        try {
            List<CellSnapshot> snapshots = new ArrayList<>();
            for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
                ControlTable table = tables.get(tableIndex);
                for (int rowIndex = 0; rowIndex < table.getRowList().size(); rowIndex++) {
                    addRowSnapshots(snapshots, tableIndex, rowIndex, table.getRowList().get(rowIndex));
                }
            }
            return List.copyOf(snapshots);
        } catch (UnsupportedEncodingException exception) {
            throw new HwpDocumentException("셀 텍스트를 읽지 못했습니다.", exception);
        }
    }

    public void setText(CellAddress address, String value) throws HwpDocumentException {
        try {
            Cell cell = cell(address);
            int inputSize = typicalSize(address.tableIndex());
            CellContent.clear(cell, true);
            // A template spread over paragraphs ("외국어 1: …" / "외국어 2: …") goes back line by line,
            // keeping each paragraph's formatting; extra lines join the last paragraph as line breaks.
            var paragraphs = cell.getParagraphList();
            String[] lines = lines(value);
            for (int index = 0; index < lines.length; index++) {
                int target = Math.min(index, paragraphs.getParagraphCount() - 1);
                Paragraph paragraph = paragraphs.getParagraph(target);
                CellContent.useInputStyle(file, paragraph, representativeShapeId, inputSize);
                ParaText text = CellContent.text(paragraph);
                if (index > target) {
                    text.addNewCharControlChar().setCode(LINE_BREAK);
                }
                text.addString(lines[index]);
                paragraph.deleteLineSeg();
            }
        } catch (Exception exception) { // no-excuse-ok: catch - translate hwplib cell failures at this boundary
            throw invalidAddress(address, exception);
        }
    }

    /** Keeps what the cell says ("성명(한글)") and writes the value on the lines under it. */
    public void appendText(CellAddress address, String value) throws HwpDocumentException {
        try {
            int inputSize = typicalSize(address.tableIndex());
            var paragraphs = cell(address).getParagraphList();
            Paragraph paragraph = paragraphs.getParagraph(paragraphs.getParagraphCount() - 1);
            ParaText text = CellContent.text(paragraph);
            int start = text.getCharList().size();
            for (String line : lines(value)) {
                text.addNewCharControlChar().setCode(LINE_BREAK);
                text.addString(line);
            }
            CellContent.styleAppendedText(file, paragraph, representativeShapeId, inputSize, start + 1);
            paragraph.deleteLineSeg();
        } catch (Exception exception) { // no-excuse-ok: catch - translate hwplib append failures at this boundary
            throw invalidAddress(address, exception);
        }
    }

    private int typicalSize(int tableIndex) {
        return typicalSizes.computeIfAbsent(tableIndex, index -> CellContent.typicalSize(file,
                tables.get(index).getRowList().stream().flatMap(row -> row.getCellList().stream()).toList()));
    }

    private List<Cell> allCells() {
        return tables.stream().flatMap(table -> table.getRowList().stream())
                .flatMap(row -> row.getCellList().stream()).toList();
    }

    private static String[] lines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }

    public void insertImage(CellAddress address, Path image) throws HwpDocumentException {
        try {
            ControlTable table = tables.get(address.tableIndex());
            Cell target = cell(address);
            normalizeMergedPhotoCell(table, address.rowIndex(), target);
            CellImageInserter.insert(file, table.getTable(), target, image);
        } catch (Exception exception) { // no-excuse-ok: catch - translate hwplib image failures at this boundary
            throw new HwpDocumentException("사진을 문서에 넣지 못했습니다: " + address, exception);
        }
    }

    /** Some HWP forms record only one grid column's width and one row's height on a merged portrait cell. */
    private static void normalizeMergedPhotoCell(ControlTable table, int rowIndex, Cell cell) {
        var header = cell.getListHeader();
        if (header.getColSpan() < 2 || header.getRowSpan() < 3 || header.getWidth() >= 5_000) {
            return;
        }
        Row row = table.getRowList().get(rowIndex);
        long usedWidth = row.getCellList().stream().mapToLong(other -> other.getListHeader().getWidth()).sum();
        long correctedWidth = header.getWidth() + table.getHeader().getWidth() - usedWidth;
        if (correctedWidth <= 5_000 || correctedWidth >= table.getHeader().getWidth()) {
            return;
        }
        header.setWidth(Math.toIntExact(correctedWidth));
        header.setHeight(Math.toIntExact(header.getHeight() * header.getRowSpan()));
    }

    private static boolean hasPicture(Cell cell) {
        for (var paragraph : cell.getParagraphList()) {
            if (paragraph.getControlList() != null && paragraph.getControlList().stream()
                    .anyMatch(control -> control.getType() == kr.dogfoot.hwplib.object.bodytext.control.ControlType.Gso)) {
                return true;
            }
        }
        return false;
    }

    /** Pictures and other drawings placed in one cell. */
    int drawingCount(CellAddress address) {
        int count = 0;
        for (var paragraph : cell(address).getParagraphList()) {
            if (paragraph.getControlList() != null) {
                count += (int) paragraph.getControlList().stream()
                        .filter(control -> control.getType() == kr.dogfoot.hwplib.object.bodytext.control.ControlType.Gso)
                        .count();
            }
        }
        return count;
    }

    public int embeddedImageCount() {
        return file.getBinData().getEmbeddedBinaryDataList().size();
    }

    public void save(Path output) throws HwpDocumentException {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            HWPWriter.toFile(file, output.toString());
        } catch (Exception exception) { // no-excuse-ok: catch - translate hwplib writer failures at this boundary
            throw new HwpDocumentException("완성 문서를 저장하지 못했습니다.", exception);
        }
    }

    private void addRowSnapshots(
            List<CellSnapshot> snapshots, int tableIndex, int rowIndex, Row row)
            throws UnsupportedEncodingException {
        for (int cellIndex = 0; cellIndex < row.getCellList().size(); cellIndex++) {
            Cell cell = row.getCellList().get(cellIndex);
            snapshots.add(new CellSnapshot(
                    new CellAddress(tableIndex, rowIndex, cellIndex),
                    cell.getListHeader().getColIndex(),
                    cell.getListHeader().getRowIndex(),
                    cell.getListHeader().getColSpan(),
                    cell.getListHeader().getRowSpan(),
                    cell.getListHeader().getWidth(),
                    cell.getListHeader().getHeight(),
                    cell.getParagraphList().getNormalString().strip(),
                    hasPicture(cell)));
        }
    }

    private Cell cell(CellAddress address) {
        return tables.get(address.tableIndex())
                .getRowList().get(address.rowIndex())
                .getCellList().get(address.cellIndex());
    }

    private static HwpDocumentException invalidAddress(CellAddress address, Exception cause) {
        return new HwpDocumentException("문서에 없는 셀입니다: " + address, cause);
    }
}
