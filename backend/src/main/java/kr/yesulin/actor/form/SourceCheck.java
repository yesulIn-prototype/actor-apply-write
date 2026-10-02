package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellSnapshot;

/** Holds a definition against the form file it is for: every cell exists and every edit finds its text. */
final class SourceCheck {
    private SourceCheck() {}

    static List<String> problems(FormDefinition definition, List<CellSnapshot> cells) {
        Map<CellAddress, CellSnapshot> byAddress = cells.stream()
                .collect(Collectors.toMap(CellSnapshot::address, Function.identity()));
        List<String> problems = new ArrayList<>();
        for (FormOutput output : definition.outputs()) {
            String where = "outputs (" + Cells.format(output.cell()) + ")";
            if (output instanceof FormOutput.Rows rows) {
                rows(rows, byAddress, where, problems);
                continue;
            }
            CellSnapshot cell = byAddress.get(output.cell());
            if (cell == null) {
                problems.add(where + ": 원본 지원서에 이 칸이 없습니다.");
                continue;
            }
            if (output instanceof FormOutput.Edit edit) {
                for (FormOutput.Replacement replacement : edit.replacements()) {
                    if (!cell.text().contains(replacement.find())) {
                        problems.add(where + ": 칸 원문에 '" + replacement.find() + "'가 없습니다. (원문: '"
                                + shown(cell.text()) + "')");
                    }
                }
            }
        }
        return problems;
    }

    /** The form's own rows must all be there; with none, the row new ones go under must be. */
    private static void rows(
            FormOutput.Rows rows, Map<CellAddress, CellSnapshot> byAddress, String where, List<String> problems) {
        CellAddress cell = rows.cell();
        if (rows.formRows() == 0) {
            if (!byAddress.containsKey(new CellAddress(cell.tableIndex(), cell.rowIndex() - 1, 0))) {
                problems.add(where + ": 새 줄이 붙을 바로 위 줄(" + (cell.rowIndex() - 1) + "행)이 원본 지원서에 없습니다.");
            }
            return;
        }
        rows.formCells().stream()
                .filter(address -> !byAddress.containsKey(address))
                .findFirst()
                .ifPresent(missing -> problems.add(where + ": 줄 표 칸 " + Cells.format(missing)
                        + "이 원본 지원서에 없습니다. formRows와 columns 수를 확인하세요."));
    }

    static Map<CellAddress, String> texts(List<CellSnapshot> cells) {
        return cells.stream().collect(Collectors.toMap(CellSnapshot::address, CellSnapshot::text));
    }

    /** A cell's text on one line, cut short, so the operator can see what the find has to match. */
    private static String shown(String text) {
        String line = text.replace("\n", " / ");
        return line.length() > 60 ? line.substring(0, 60) + "…" : line;
    }
}
