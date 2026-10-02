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

    static Map<CellAddress, String> texts(List<CellSnapshot> cells) {
        return cells.stream().collect(Collectors.toMap(CellSnapshot::address, CellSnapshot::text));
    }

    /** A cell's text on one line, cut short, so the operator can see what the find has to match. */
    private static String shown(String text) {
        String line = text.replace("\n", " / ");
        return line.length() > 60 ? line.substring(0, 60) + "…" : line;
    }
}
