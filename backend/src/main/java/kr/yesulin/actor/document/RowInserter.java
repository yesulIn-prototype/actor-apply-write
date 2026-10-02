package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adds table rows to a copy of a form with rhwp, for answers that run past the rows the form has. A new row
 * comes out of {@code edit insert-row} with every grid column as its own cell, so the cells the copied row had
 * merged ("경력" lines spanning the whole table) are merged again. The copy is checked afterwards: each table
 * grew by its rows (rhwp numbers top-level tables only, so a form with tables inside tables could grow the
 * wrong one), and every new row has as many cells as the row it copies, or answers would land in the wrong cells.
 */
final class RowInserter {
    private final PdfConverter rhwp;

    RowInserter(PdfConverter rhwp) {
        this.rhwp = rhwp;
    }

    /** Returns a new file in {@code directory}; the caller deletes it. */
    Path grow(Path source, Path directory, List<TableGrowth> growths) throws IOException, HwpDocumentException {
        List<CellSnapshot> blank = HwpDocument.open(source).cells();
        Path current = directory.resolve("grown-" + UUID.randomUUID() + ".hwp");
        Files.copy(source, current);
        try {
            // Bottom rows first, so the rows a later growth names have not moved yet.
            List<TableGrowth> order = growths.stream()
                    .sorted(Comparator.comparingInt(TableGrowth::tableIndex)
                            .thenComparing(Comparator.comparingInt(TableGrowth::belowRow).reversed()))
                    .toList();
            for (TableGrowth growth : order) {
                List<CellSnapshot> copied = copiedRow(blank, growth);
                for (int added = 0; added < growth.count(); added++) {
                    current = step(current, directory,
                            (from, to) -> rhwp.insertRow(from, to, growth.tableIndex(), growth.belowRow()));
                }
                for (int row = growth.belowRow() + 1; row <= growth.belowRow() + growth.count(); row++) {
                    for (CellSnapshot cell : copied) {
                        if (cell.columnSpan() > 1) {
                            int at = row;
                            current = step(current, directory, (from, to) -> rhwp.mergeCells(from, to,
                                    growth.tableIndex(), at, cell.columnAddress(),
                                    cell.columnAddress() + cell.columnSpan() - 1));
                        }
                    }
                }
            }
            check(blank, current, growths);
            return current;
        } catch (IOException | HwpDocumentException | RuntimeException failure) {
            Files.deleteIfExists(current);
            throw failure;
        }
    }

    private interface Edit {
        void apply(Path from, Path to) throws IOException, HwpDocumentException;
    }

    private static Path step(Path current, Path directory, Edit edit) throws IOException, HwpDocumentException {
        Path next = directory.resolve("grown-" + UUID.randomUUID() + ".hwp");
        edit.apply(current, next);
        Files.delete(current);
        return next;
    }

    /** The row a growth copies; it must own every grid column, with nothing merged down into or out of it. */
    private static List<CellSnapshot> copiedRow(List<CellSnapshot> blank, TableGrowth growth)
            throws HwpDocumentException {
        List<CellSnapshot> table = blank.stream()
                .filter(cell -> cell.address().tableIndex() == growth.tableIndex())
                .toList();
        List<CellSnapshot> row = table.stream()
                .filter(cell -> cell.address().rowIndex() == growth.belowRow())
                .toList();
        int width = table.stream().mapToInt(cell -> cell.columnAddress() + cell.columnSpan()).max().orElse(0);
        boolean whole = row.stream().mapToInt(CellSnapshot::columnSpan).sum() == width;
        if (row.isEmpty() || !whole || row.stream().anyMatch(cell -> cell.rowSpan() > 1)) {
            throw new HwpDocumentException("표 " + growth.tableIndex() + "의 " + growth.belowRow()
                    + "행은 위아래 줄과 병합된 칸이 있어 줄을 늘릴 수 없습니다.");
        }
        return row;
    }

    private static void check(List<CellSnapshot> blank, Path grown, List<TableGrowth> growths)
            throws HwpDocumentException {
        Map<List<Integer>, Long> before = cellsPerRow(blank);
        Map<List<Integer>, Long> after = cellsPerRow(HwpDocument.open(grown).cells());
        for (TableGrowth growth : growths) {
            int added = growths.stream()
                    .filter(other -> other.tableIndex() == growth.tableIndex())
                    .mapToInt(TableGrowth::count)
                    .sum();
            int above = growths.stream()
                    .filter(other -> other.tableIndex() == growth.tableIndex() && other.belowRow() < growth.belowRow())
                    .mapToInt(TableGrowth::count)
                    .sum();
            long expected = before.getOrDefault(List.of(growth.tableIndex(), growth.belowRow()), 0L);
            boolean even = rows(after, growth.tableIndex()) == rows(before, growth.tableIndex()) + added;
            for (int row = growth.belowRow() + above; row <= growth.belowRow() + above + growth.count(); row++) {
                even &= after.getOrDefault(List.of(growth.tableIndex(), row), 0L) == expected;
            }
            if (!even) {
                throw new HwpDocumentException("표 " + growth.tableIndex() + "에 줄을 늘리지 못했습니다. "
                        + "표 안에 표가 있는 양식은 줄을 늘릴 수 없습니다.");
            }
        }
    }

    private static Map<List<Integer>, Long> cellsPerRow(List<CellSnapshot> cells) {
        return cells.stream().collect(Collectors.groupingBy(
                cell -> List.of(cell.address().tableIndex(), cell.address().rowIndex()), Collectors.counting()));
    }

    private static long rows(Map<List<Integer>, Long> cellsPerRow, int table) {
        return cellsPerRow.keySet().stream().filter(key -> key.getFirst() == table).count();
    }
}
