package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.yesulin.actor.document.CellAddress;
import tools.jackson.databind.JsonNode;

/** Reads the "outputs" of a definition: one cell each, written one way. */
final class OutputParser {
    private static final Pattern CELL = Pattern.compile("^(\\d{1,3})\\.(\\d{1,4})\\.(\\d{1,4})$");
    private static final Pattern CONDITION = Pattern.compile("^([A-Za-z][A-Za-z0-9_]{0,39})(?:=([A-Za-z][A-Za-z0-9_]{0,39}))?$");
    private static final Set<String> KEYS = Set.of("cell", "text", "append", "edit", "photo", "join");
    private static final List<String> WAYS = List.of("text", "append", "edit", "photo");

    private final List<String> problems;

    OutputParser(List<String> problems) {
        this.problems = problems;
    }

    List<FormOutput> parse(JsonNode outputs) {
        if (!outputs.isArray() || outputs.isEmpty()) {
            problems.add("outputs: 문서에 값을 넣을 칸을 하나 이상 정의해야 합니다.");
            return List.of();
        }
        List<FormOutput> parsed = new ArrayList<>();
        Set<CellAddress> cells = new HashSet<>();
        int index = 0;
        for (JsonNode node : outputs) {
            String where = "outputs[" + index++ + "]";
            FormOutput output = output(where, node);
            if (output == null) {
                continue;
            }
            if (!cells.add(output.cell())) {
                problems.add(where + ": 칸 " + Cells.format(output.cell()) + "에 출력이 두 번 정의됐습니다. 복합 입력은 한 템플릿에 함께 쓰세요.");
                continue;
            }
            parsed.add(output);
        }
        return parsed;
    }

    private FormOutput output(String where, JsonNode node) {
        if (!node.isObject()) {
            problems.add(where + ": 출력은 객체여야 합니다.");
            return null;
        }
        Json.unknownKeys(where, node, KEYS, problems);
        CellAddress cell = cell(where, Json.text(node, "cell"));
        List<String> ways = WAYS.stream().filter(node::has).toList();
        if (ways.size() != 1) {
            problems.add(where + ": text, append, edit, photo 중 정확히 하나를 지정해야 합니다.");
            return null;
        }
        if (cell == null) {
            return null;
        }
        where = where + " (" + Cells.format(cell) + ")";
        String way = ways.getFirst();
        if (node.has("join") && !(way.equals("text") || way.equals("append"))) {
            problems.add(where + ": join은 text·append에서만 쓸 수 있습니다.");
        }
        String join = node.has("join") ? Json.text(node, "join") : ", ";
        return switch (way) {
            case "text" -> new FormOutput.Text(cell, template(where, node, "text"), join);
            case "append" -> new FormOutput.Append(cell, template(where, node, "append"), join);
            case "photo" -> new FormOutput.Photo(cell, Json.text(node, "photo"));
            default -> new FormOutput.Edit(cell, replacements(where, node.path("edit")));
        };
    }

    private CellAddress cell(String where, String raw) {
        Matcher matcher = CELL.matcher(raw.strip());
        if (!matcher.matches()) {
            problems.add(where + ": cell은 \"표.행.칸\" 형식이어야 합니다. (현재: '" + raw + "')");
            return null;
        }
        return new CellAddress(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    private String template(String where, JsonNode node, String key) {
        String template = Json.text(node, key);
        if (template.isBlank()) {
            problems.add(where + ": " + key + "에 넣을 내용이 비어 있습니다.");
        }
        return template;
    }

    private List<FormOutput.Replacement> replacements(String where, JsonNode edits) {
        if (!edits.isArray() || edits.isEmpty()) {
            problems.add(where + ": edit에는 바꿀 내용을 하나 이상 적어야 합니다.");
            return List.of();
        }
        List<FormOutput.Replacement> replacements = new ArrayList<>();
        int index = 0;
        for (JsonNode edit : edits) {
            String at = where + ".edit[" + index++ + "]";
            Json.unknownKeys(at, edit, Set.of("find", "replace", "when"), problems);
            String find = Json.text(edit, "find");
            if (find.isEmpty()) {
                problems.add(at + ": find(원문에서 찾을 글자)가 필요합니다.");
                continue;
            }
            replacements.add(new FormOutput.Replacement(find, Json.text(edit, "replace"), condition(at, Json.text(edit, "when"))));
        }
        return replacements;
    }

    private FormOutput.Condition condition(String where, String raw) {
        if (raw.isBlank()) {
            return new FormOutput.Condition.Always();
        }
        Matcher matcher = CONDITION.matcher(raw.strip());
        if (!matcher.matches()) {
            problems.add(where + ": when은 \"항목\" 또는 \"항목=선택지\" 형식이어야 합니다. (현재: '" + raw + "')");
            return new FormOutput.Condition.Always();
        }
        return matcher.group(2) == null
                ? new FormOutput.Condition.Answered(matcher.group(1))
                : new FormOutput.Condition.Picked(matcher.group(1), matcher.group(2));
    }
}
