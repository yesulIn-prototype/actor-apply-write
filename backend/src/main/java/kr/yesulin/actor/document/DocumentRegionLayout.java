package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Resolves body paragraphs by renderer pi, and table cells by the existing verified grid mapping. */
final class DocumentRegionLayout {
    private DocumentRegionLayout() {}

    static Map<String, List<PageLayout.Box>> boxes(PageLayout layout, Path directory) throws IOException {
        Map<String, List<PageLayout.Box>> result = new LinkedHashMap<>();
        layout.cells().forEach((address, boxes) -> result.put("c:" + address.tableIndex() + "."
                + address.rowIndex() + "." + address.cellIndex(), boxes));
        var json = JsonMapper.builder().build();
        try (var files = Files.list(directory.resolve("layout"))) {
            var trees = files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
            for (int page = 0; page < trees.size(); page++) {
                collect(json.readTree(trees.get(page).toFile()), page + 1, false, result);
            }
        }
        return result;
    }

    private static void collect(JsonNode node, int page, boolean body, Map<String, List<PageLayout.Box>> boxes) {
        String type = node.path("type").asString("");
        boolean inBody = body || type.equals("Body");
        if (inBody && type.equals("TextLine") && node.has("pi")) {
            var box = node.path("bbox");
            boxes.computeIfAbsent("p:0." + node.path("pi").asInt(), ignored -> new ArrayList<>())
                    .add(new PageLayout.Box(page, box.path("x").asDouble(), box.path("y").asDouble(),
                            box.path("w").asDouble(), box.path("h").asDouble()));
            return;
        }
        if (type.equals("Page") || type.equals("Body") || type.equals("Column")) {
            for (JsonNode child : node.path("children")) collect(child, page, inBody, boxes);
        }
    }
}
