package kr.yesulin.actor.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import kr.yesulin.actor.document.CellText.Role;
import kr.yesulin.actor.document.FieldCandidate.FieldKind;
import kr.yesulin.actor.document.FieldCandidate.InputStyle;

/**
 * A header row of labels followed by rows whose cells line up under it:
 * 학력 (출신학교 | 지역 | 졸업년도 | 전공) or 공연경력 (No. | 장르 | 공연명 | …).
 * Each input becomes a field tagged with its table, row and column, so the form can show
 * the table row by row instead of dozens of loose inputs.
 */
final class TableExtractor {
    private final FieldExtractor.Layout layout;
    private final List<FieldCandidate> fields;
    private final Set<CellAddress> claimed;
    /** Rows already numbered per group: a table continued on the next page ("경력사항" again) keeps counting. */
    private final java.util.Map<String, Integer> numberedRows = new java.util.HashMap<>();

    TableExtractor(FieldExtractor.Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        this.layout = layout;
        this.fields = fields;
        this.claimed = claimed;
    }

    void extract() {
        for (List<CellSnapshot> row : layout.rows()) {
            // "학력" spanning the whole table on the left names the table; it is not a column.
            CellSnapshot section = row.getFirst().rowSpan() > 1 && CellText.role(row.getFirst().text()) == Role.LABEL
                    ? row.getFirst()
                    : null;
            List<CellSnapshot> header = section == null ? row : row.subList(1, row.size());
            if (header.size() < 2 || header.stream().anyMatch(cell -> claimed.contains(cell.address())
                    || CellText.role(cell.text()) != Role.LABEL)) {
                continue;
            }
            List<List<CellSnapshot>> body = bodyRows(header);
            if (!body.isEmpty()) {
                addTable(section, header, body);
            }
        }
    }

    private List<List<CellSnapshot>> bodyRows(List<CellSnapshot> header) {
        List<List<CellSnapshot>> body = new ArrayList<>();
        int table = header.getFirst().address().tableIndex();
        for (int rowIndex = header.getFirst().address().rowIndex() + 1; ; rowIndex++) {
            List<CellSnapshot> row = layout.row(table, rowIndex);
            if (!alignsWith(header, row)) {
                return body;
            }
            for (int index = 1; index < row.size(); index++) {
                if (CellText.role(row.get(index).text()) == Role.LABEL || claimed.contains(row.get(index).address())) {
                    return body;
                }
            }
            body.add(row);
        }
    }

    private void addTable(CellSnapshot section, List<CellSnapshot> header, List<List<CellSnapshot>> body) {
        String group = section != null ? CellText.label(section.text()) : groupName(header);
        int numberColumn = -1;
        for (int column = 0; column < header.size(); column++) {
            if (FieldExtractor.isNumberHeader(header.get(column).text())) {
                numberColumn = column;
            }
        }
        if (section != null) {
            claimed.add(section.address());
        }
        header.forEach(cell -> claimed.add(cell.address()));
        int offset = numberedRows.getOrDefault(group, 0);
        for (int index = 0; index < body.size(); index++) {
            List<CellSnapshot> row = body.get(index);
            Role first = CellText.role(row.getFirst().text());
            boolean rowHeading = numberColumn != 0 && first == Role.LABEL;
            String rowName = rowName(row, offset + index, numberColumn, first);
            for (int column = 0; column < row.size(); column++) {
                CellSnapshot cell = row.get(column);
                claimed.add(cell.address());
                Role role = CellText.role(cell.text());
                if (column == numberColumn || (column == 0 && rowHeading) || role == Role.SKIP) {
                    continue;
                }
                String columnName = CellText.label(header.get(column).text());
                String label = FieldExtractor.isNumber(rowName) ? columnName + " " + rowName : rowName + " " + columnName;
                InputStyle style = role == Role.EMPTY ? InputStyle.BLANK
                        : role == Role.TEMPLATE ? InputStyle.TEMPLATE : InputStyle.GUIDE;
                fields.add(FieldExtractor.tableField(FieldKind.TEXT, label, "", cell, style, false,
                        group, offset + index + 1, rowName, columnName));
            }
        }
        numberedRows.put(group, offset + body.size());
    }

    private static String rowName(List<CellSnapshot> row, int index, int numberColumn, Role first) {
        if (numberColumn >= 0) {
            String number = row.get(numberColumn).text().strip();
            return number.isEmpty() ? String.valueOf(index + 1) : number.replaceAll("\\.$", "");
        }
        if (first == Role.LABEL) {
            return CellText.label(row.getFirst().text());
        }
        if (first == Role.TEMPLATE) {
            String words = CellText.templateWords(row.getFirst().text());
            if (!words.isBlank()) {
                return words;
            }
        }
        return String.valueOf(index + 1);
    }

    /** The full-width title above the header ("공연경력", "경력사항 - ※ …" → "경력사항"). */
    private String groupName(List<CellSnapshot> header) {
        CellSnapshot first = header.getFirst();
        List<CellSnapshot> above = layout.row(first.address().tableIndex(), first.address().rowIndex() - 1);
        Role aboveRole = above.size() == 1 ? CellText.role(above.getFirst().text()) : Role.EMPTY;
        if (aboveRole == Role.LABEL || aboveRole == Role.NOTE) {
            claimed.add(above.getFirst().address());
            return CellText.label(above.getFirst().text()).split("\\s[-–—※(]|[※(]")[0].strip();
        }
        return String.join(" · ", header.stream().map(cell -> CellText.label(cell.text())).toList());
    }

    private static boolean alignsWith(List<CellSnapshot> header, List<CellSnapshot> row) {
        if (row.size() != header.size()) {
            return false;
        }
        for (int index = 0; index < header.size(); index++) {
            if (header.get(index).columnAddress() != row.get(index).columnAddress()
                    || header.get(index).columnSpan() != row.get(index).columnSpan()) {
                return false;
            }
        }
        return true;
    }
}
