package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * A notice on the standard form, as the operator writes it: the base form plus what this notice adds or leaves
 * out. {@link StandardForms} turns it into the notice's own form file and full definition.
 *
 * @param roles     the roles to pick from, in the notice's words; empty to take the role as text
 * @param pickRoles most roles one applicant may pick; 0 for no limit
 * @param drop      rows of 지원 정보 the notice does not ask ("role", "current", "unavailable")
 * @param extras    questions added under 지원 정보, in order: each a full definition item
 * @param help      new help text by item id ("최근 6개월 이내 사진")
 */
record StandardSpec(String base, String title, String fileName, List<String> roles, int pickRoles, Set<String> drop,
        List<ObjectNode> extras, Map<String, String> help, JsonNode submission) {
    static final List<String> INFO_ROWS = List.of("role", "current", "unavailable");
    private static final Set<String> KEYS = Set.of(
            "base", "title", "fileName", "note", "roles", "pickRoles", "drop", "extras", "help", "submission");
    private static final Set<String> CUSTOM_TYPES = Set.of("text", "yesno", "single", "multi");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * @param catalog  the base form's extras ({@code standard-v1.extras.json} "extras")
     * @param baseIds  the base form's own item ids, which a custom question may not take
     */
    static StandardSpec parse(JsonNode root, JsonNode catalog, Set<String> baseIds, List<String> problems) {
        Json.unknownKeys("공고 설정", root, KEYS, problems);
        String title = Json.text(root, "title").strip();
        if (title.isEmpty()) {
            problems.add("title: 배우 화면과 지원서에 들어갈 공고 제목이 필요합니다.");
        }
        List<String> roles = strings("roles", root.path("roles"), problems);
        if (roles.size() == 1) {
            problems.add("roles: 배역이 하나뿐이면 목록 대신 drop에 \"role\"을 넣으세요.");
        }
        int pickRoles = Json.integer(root, "pickRoles", 1);
        if (pickRoles < 0 || pickRoles > Math.max(roles.size(), 1)) {
            problems.add("pickRoles: 0(제한 없음)부터 배역 수까지만 쓸 수 있습니다.");
        }
        Set<String> drop = new HashSet<>(strings("drop", root.path("drop"), problems));
        drop.stream().filter(row -> !INFO_ROWS.contains(row))
                .forEach(row -> problems.add("drop: 뺄 수 있는 줄은 role, current, unavailable입니다. (현재: '" + row + "')"));
        if (drop.contains("role") && !roles.isEmpty()) {
            problems.add("drop: 배역 목록(roles)을 적었으면 role 줄을 뺄 수 없습니다.");
        }
        Map<String, String> help = new LinkedHashMap<>();
        root.path("help").properties().forEach(entry -> help.put(entry.getKey(), entry.getValue().asString()));
        return new StandardSpec(Json.text(root, "base").strip(), title, Json.text(root, "fileName").strip(), roles,
                pickRoles, drop, extras(root.path("extras"), catalog, baseIds, problems), help, root.path("submission"));
    }

    private static List<ObjectNode> extras(JsonNode node, JsonNode catalog, Set<String> baseIds, List<String> problems) {
        List<ObjectNode> extras = new ArrayList<>();
        Set<String> ids = new HashSet<>(baseIds);
        int index = 0;
        for (JsonNode entry : node) {
            String where = "extras[" + index++ + "]";
            ObjectNode item = entry.has("custom") ? custom(where, entry.path("custom"), problems)
                    : fromCatalog(where, entry, catalog, problems);
            if (item == null) {
                continue;
            }
            if (!ids.add(item.path("id").asString())) {
                problems.add(where + ": 항목 id '" + item.path("id").asString() + "'가 겹칩니다.");
                continue;
            }
            if (entry.path("required").asBoolean(false)) {
                item.put("required", true);
            }
            extras.add(item);
        }
        return extras;
    }

    /** "video", or { "use": "auditionDates", "options": [...], "required": true }. */
    private static ObjectNode fromCatalog(String where, JsonNode entry, JsonNode catalog, List<String> problems) {
        String key = entry.isString() ? entry.asString() : Json.text(entry, "use");
        JsonNode known = catalog.path(key);
        if (!known.isObject()) {
            problems.add(where + ": 추가 항목 목록에 '" + key + "'이 없습니다. 목록: " + String.join(", ", catalog.propertyNames()));
            return null;
        }
        if (!entry.isString()) {
            Json.unknownKeys(where, entry, Set.of("use", "options", "required"), problems);
        }
        ObjectNode item = NODES.objectNode().put("id", key).put("label", known.path("label").asString());
        item.setAll((ObjectNode) known.path("item").deepCopy());
        if (known.path("operatorOptions").asBoolean(false)) {
            List<String> options = strings(where + ".options", entry.path("options"), problems);
            if (options.size() < 2) {
                problems.add(where + ": '" + key + "'에는 고를 선택지(options)를 2개 이상 적어야 합니다.");
            }
            item.set("options", options(options));
        } else if (entry.has("options")) {
            problems.add(where + ": '" + key + "'은 선택지를 정해 둔 항목이라 options를 쓸 수 없습니다.");
        }
        return item;
    }

    /** { "id": "q1", "label": "…", "type": "text" | "yesno" | "single" | "multi", "options": [...], "help": "…" }. */
    private static ObjectNode custom(String where, JsonNode custom, List<String> problems) {
        Json.unknownKeys(where + ".custom", custom, Set.of("id", "label", "type", "options", "help"), problems);
        String id = Json.text(custom, "id");
        String label = Json.text(custom, "label").strip();
        String type = Json.text(custom, "type");
        if (!Placeholders.ITEM_ID.matcher(id).matches() || label.isEmpty() || !CUSTOM_TYPES.contains(type)) {
            problems.add(where + ".custom: 영문 id, label, type(text·yesno·single·multi)이 필요합니다.");
            return null;
        }
        ObjectNode item = NODES.objectNode().put("id", id).put("label", label);
        if (!Json.text(custom, "help").isBlank()) {
            item.put("help", Json.text(custom, "help").strip());
        }
        switch (type) {
            case "text" -> item.put("type", "text").put("maxLength", 200);
            case "yesno" -> item.put("type", "single").set("options", NODES.arrayNode()
                    .add(NODES.objectNode().put("id", "yes").put("label", "예"))
                    .add(NODES.objectNode().put("id", "no").put("label", "아니오")));
            default -> {
                List<String> options = strings(where + ".custom.options", custom.path("options"), problems);
                item.put("type", type).set("options", options(options));
            }
        }
        return item;
    }

    static ArrayNode options(List<String> labels) {
        ArrayNode options = NODES.arrayNode();
        for (int at = 0; at < labels.size(); at++) {
            options.add(NODES.objectNode().put("id", "o" + (at + 1)).put("label", labels.get(at)));
        }
        return options;
    }

    private static List<String> strings(String where, JsonNode node, List<String> problems) {
        if (node.isMissingNode()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            problems.add(where + ": 글자 목록([\"…\", \"…\"])이어야 합니다.");
            return values;
        }
        for (JsonNode value : node) {
            String text = value.isString() ? value.asString().strip() : "";
            if (text.isEmpty()) {
                problems.add(where + ": 빈 값이나 글자가 아닌 값이 있습니다.");
            } else {
                values.add(text);
            }
        }
        return values;
    }
}
