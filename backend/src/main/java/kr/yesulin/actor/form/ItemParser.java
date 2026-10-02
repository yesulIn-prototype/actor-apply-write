package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Reads the "items" of a definition, asking only for the settings each type uses. */
final class ItemParser {
    private static final int SINGLE_LINE_LIMIT = 200;
    private static final int MULTILINE_LIMIT = 2000;
    private static final int LONGEST = 5000;
    private static final Set<String> KEYS = Set.of(
            "id", "label", "help", "required", "type", "multiline", "maxLength", "options", "min", "max");

    private final List<String> problems;

    ItemParser(List<String> problems) {
        this.problems = problems;
    }

    List<FormItem> parse(JsonNode items) {
        if (!items.isArray() || items.isEmpty()) {
            problems.add("items: 입력 항목을 하나 이상 정의해야 합니다.");
            return List.of();
        }
        List<FormItem> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (JsonNode node : items) {
            String where = "items[" + index++ + "]";
            FormItem item = item(where, node);
            if (item == null) {
                continue;
            }
            if (!ids.add(item.id())) {
                problems.add(where + ": id '" + item.id() + "'가 중복됩니다.");
                continue;
            }
            parsed.add(item);
        }
        return parsed;
    }

    private FormItem item(String where, JsonNode node) {
        if (!node.isObject()) {
            problems.add(where + ": 항목은 객체여야 합니다.");
            return null;
        }
        Json.unknownKeys(where, node, KEYS, problems);
        String id = Json.text(node, "id");
        if (!Placeholders.ITEM_ID.matcher(id).matches()) {
            problems.add(where + ": id는 영문으로 시작하는 영문·숫자·_ 40자 이내여야 합니다. (현재: '" + id + "')");
            return null;
        }
        where = where + " (" + id + ")";
        String label = Json.text(node, "label").strip();
        if (label.isEmpty()) {
            problems.add(where + ": label(화면에 보일 이름)이 필요합니다.");
        }
        FormItem.Type type = type(where, Json.text(node, "type"));
        if (type == null) {
            return null;
        }
        boolean text = type == FormItem.Type.TEXT;
        boolean choice = type == FormItem.Type.SINGLE || type == FormItem.Type.MULTI;
        onlyFor(where, node, "multiline", text, "자유 텍스트");
        onlyFor(where, node, "maxLength", text, "자유 텍스트");
        onlyFor(where, node, "options", choice, "단일·다중 선택");
        onlyFor(where, node, "min", type == FormItem.Type.MULTI, "다중 선택");
        onlyFor(where, node, "max", type == FormItem.Type.MULTI, "다중 선택");

        boolean multiline = text && Json.bool(node, "multiline");
        int maxLength = text ? maxLength(where, node, multiline) : 0;
        List<FormItem.Option> options = choice ? options(where, node.path("options")) : List.of();
        int min = type == FormItem.Type.MULTI ? Json.integer(node, "min", 0) : 0;
        int max = type == FormItem.Type.MULTI ? Json.integer(node, "max", 0) : 0;
        if (type == FormItem.Type.MULTI && (min < 0 || max < 0 || (max > 0 && min > max) || max > options.size())) {
            problems.add(where + ": min·max는 0 이상이고 min ≤ max ≤ 선택지 수여야 합니다.");
        }
        return new FormItem(id, label, Json.text(node, "help").strip(), Json.bool(node, "required"), type,
                multiline, maxLength, options, min, max);
    }

    private FormItem.Type type(String where, String raw) {
        try {
            return FormItem.Type.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            problems.add(where + ": type은 text, phone, single, multi, photo 중 하나여야 합니다. (현재: '" + raw + "')");
            return null;
        }
    }

    private int maxLength(String where, JsonNode node, boolean multiline) {
        int fallback = multiline ? MULTILINE_LIMIT : SINGLE_LINE_LIMIT;
        int value = Json.integer(node, "maxLength", fallback);
        if (value < 1 || value > LONGEST) {
            problems.add(where + ": maxLength는 1~" + LONGEST + " 사이여야 합니다.");
            return fallback;
        }
        return value;
    }

    private List<FormItem.Option> options(String where, JsonNode node) {
        if (!node.isArray() || node.size() < 2) {
            problems.add(where + ": 선택형에는 options가 2개 이상 필요합니다.");
            return List.of();
        }
        List<FormItem.Option> options = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (JsonNode option : node) {
            String at = where + ".options[" + index++ + "]";
            Json.unknownKeys(at, option, Set.of("id", "label", "output"), problems);
            String id = Json.text(option, "id");
            String label = Json.text(option, "label").strip();
            if (!Placeholders.ITEM_ID.matcher(id).matches() || label.isEmpty()) {
                problems.add(at + ": 선택지에는 영문 id와 label이 필요합니다.");
                continue;
            }
            if (!ids.add(id)) {
                problems.add(at + ": 선택지 id '" + id + "'가 중복됩니다.");
                continue;
            }
            options.add(new FormItem.Option(id, label, Json.text(option, "output")));
        }
        return options;
    }

    private void onlyFor(String where, JsonNode node, String key, boolean allowed, String types) {
        if (!allowed && node.has(key)) {
            problems.add(where + ": " + key + "는 " + types + "에서만 쓸 수 있습니다.");
        }
    }
}
