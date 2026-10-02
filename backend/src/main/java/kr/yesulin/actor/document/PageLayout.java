package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where every table cell of a rendered HWP landed: page sizes plus boxes per cell, in page units
 * (the same as the SVG's viewBox).
 */
public record PageLayout(List<PreviewResponse.Page> pages, Map<CellAddress, List<Box>> cells) {
    public record Box(int page, double x, double y, double width, double height) {}

    /** Renders {@code hwp} into {@code directory} ("pages/*.svg", "layout/*.json") and reads the cell boxes. */
    static PageLayout render(PdfConverter renderer, JsonMapper json, Path hwp, Path directory)
            throws IOException, HwpDocumentException {
        Path pages = directory.resolve("pages");
        Path layout = directory.resolve("layout");
        renderer.exportSvg(hwp, pages);
        renderer.exportLayout(hwp, layout);
        List<JsonNode> trees = new ArrayList<>();
        for (Path file : sorted(layout, ".json")) {
            trees.add(json.readTree(file.toFile()));
        }
        return read(trees, HwpDocument.open(hwp).cells());
    }

    /** The rendered page images, in page order. */
    static List<Path> pageImages(Path directory) throws IOException {
        return sorted(directory.resolve("pages"), ".svg");
    }

    static PageLayout read(List<JsonNode> trees, List<CellSnapshot> cells) {
        List<PreviewResponse.Page> pages = new ArrayList<>();
        for (int index = 0; index < trees.size(); index++) {
            JsonNode box = trees.get(index).path("bbox");
            pages.add(new PreviewResponse.Page(index + 1, box.path("w").asDouble(), box.path("h").asDouble()));
        }
        Map<String, Integer> tableIndexes = matchTables(cells, trees);
        // (table, row, column) as the renderer reports them → boxes on each page.
        Map<List<Integer>, List<Box>> rendered = new HashMap<>();
        for (int page = 0; page < trees.size(); page++) {
            collectCells(trees.get(page), page + 1, null, tableIndexes, rendered);
        }
        Map<CellAddress, List<Box>> byAddress = new LinkedHashMap<>();
        for (CellSnapshot cell : cells) {
            List<Integer> key = List.of(cell.address().tableIndex(), cell.rowAddress(), cell.columnAddress());
            List<Box> boxes = rendered.get(key);
            if (boxes != null) {
                byAddress.put(cell.address(), List.copyOf(boxes));
            }
        }
        return new PageLayout(List.copyOf(pages), byAddress);
    }

    private static void collectCells(
            JsonNode node,
            int page,
            Integer table,
            Map<String, Integer> tableIndexes,
            Map<List<Integer>, List<Box>> boxes) {
        String type = node.path("type").asString("");
        Integer current = table;
        if (type.equals("Table")) {
            // Nested tables are not fields of their own; only top-level tables are matched.
            current = table == null ? tableIndexes.getOrDefault(tableKey(node), -1) : -1;
        }
        if (type.equals("Cell") && current != null && current >= 0) {
            JsonNode box = node.path("bbox");
            boxes.computeIfAbsent(List.of(current, node.path("row").asInt(), node.path("col").asInt()),
                    ignored -> new ArrayList<>()).add(new Box(page,
                    box.path("x").asDouble(), box.path("y").asDouble(), box.path("w").asDouble(), box.path("h").asDouble()));
        }
        for (JsonNode child : node.path("children")) {
            collectCells(child, page, current, tableIndexes, boxes);
        }
    }

    /**
     * The renderer names tables by their position in the text; the parser numbers them in reading order.
     * Tables are paired in order, only when their row and column counts agree, so a nested or extra
     * table never shifts every hotspot onto the wrong table.
     */
    static Map<String, Integer> matchTables(List<CellSnapshot> cells, List<JsonNode> trees) {
        Map<Integer, int[]> sizes = new LinkedHashMap<>();
        for (CellSnapshot cell : cells) {
            int[] size = sizes.computeIfAbsent(cell.address().tableIndex(), ignored -> new int[2]);
            size[0] = Math.max(size[0], cell.rowAddress() + cell.rowSpan());
            size[1] = Math.max(size[1], cell.columnAddress() + cell.columnSpan());
        }
        Map<String, int[]> rendered = new LinkedHashMap<>();
        for (JsonNode tree : trees) {
            topLevelTables(tree, false, rendered);
        }
        Map<String, Integer> matched = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (Map.Entry<String, int[]> table : rendered.entrySet()) {
            for (int index : sizes.keySet()) {
                int[] size = sizes.get(index);
                if (!used.contains(index) && size[0] == table.getValue()[0] && size[1] == table.getValue()[1]) {
                    matched.put(table.getKey(), index);
                    used.add(index);
                    break;
                }
            }
        }
        return matched;
    }

    private static void topLevelTables(JsonNode node, boolean insideTable, Map<String, int[]> tables) {
        boolean table = node.path("type").asString("").equals("Table");
        if (table && !insideTable) {
            tables.putIfAbsent(tableKey(node), new int[] {node.path("rows").asInt(), node.path("cols").asInt()});
        }
        for (JsonNode child : node.path("children")) {
            topLevelTables(child, insideTable || table, tables);
        }
    }

    static String tableKey(JsonNode table) {
        return table.path("pi").asInt() + ":" + table.path("ci").asInt();
    }

    private static List<Path> sorted(Path directory, String extension) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(extension)).sorted().toList();
        }
    }
}
