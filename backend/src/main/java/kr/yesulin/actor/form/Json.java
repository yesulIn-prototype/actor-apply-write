package kr.yesulin.actor.form;

import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Lenient reads of operator JSON: a missing or wrongly typed value reads as empty, and the parser reports it. */
final class Json {
    private Json() {}

    static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isString() ? value.asString() : "";
    }

    static boolean bool(JsonNode node, String key) {
        return node.path(key).asBoolean(false);
    }

    static int integer(JsonNode node, String key, int fallback) {
        JsonNode value = node.path(key);
        return value.isIntegralNumber() ? value.asInt() : fallback;
    }

    /** A misspelt setting ("mutliline") would otherwise be silently ignored. */
    static void unknownKeys(String where, JsonNode node, Set<String> known, List<String> problems) {
        for (String key : node.propertyNames()) {
            if (!known.contains(key)) {
                problems.add(where + ": 알 수 없는 설정 '" + key + "'");
            }
        }
    }
}
