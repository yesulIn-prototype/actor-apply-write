package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders the completed form to page images with rhwp and maps each field's cell to where it
 * landed on the page, so tapping the preview opens that field for editing.
 */
@Service
public final class PreviewService {
    private final DocumentStore store;
    private final PdfConverter renderer;
    private final JsonMapper json;

    public PreviewService(DocumentStore store, PdfConverter renderer, JsonMapper json) {
        this.store = store;
        this.renderer = renderer;
        this.json = json;
    }

    public synchronized PreviewResponse preview(UUID documentId) throws IOException, HwpDocumentException {
        StoredDocument stored = store.require(documentId);
        Path manifest = directory(stored).resolve("preview.json");
        if (Files.exists(manifest)) {
            return json.readValue(manifest.toFile(), PreviewResponse.class);
        }
        Path hwp = completed(stored);
        Path pages = directory(stored).resolve("pages");
        Path layout = directory(stored).resolve("layout");
        renderer.exportSvg(hwp, pages);
        renderer.exportLayout(hwp, layout);

        List<JsonNode> trees = new ArrayList<>();
        for (Path file : sorted(layout, ".json")) {
            trees.add(json.readTree(file.toFile()));
        }
        PreviewResponse preview = new PreviewResponse(pages(trees), hotspots(stored, trees));
        json.writeValue(manifest.toFile(), preview);
        return preview;
    }

    public byte[] page(UUID documentId, int number) throws IOException, HwpDocumentException {
        StoredDocument stored = store.require(documentId);
        preview(documentId);
        List<Path> svgs = sorted(directory(stored).resolve("pages"), ".svg");
        if (number < 1 || number > svgs.size()) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        return Files.readAllBytes(svgs.get(number - 1));
    }

    /** Drops the rendered preview; called whenever the completed file changes. */
    static void invalidate(StoredDocument stored) throws IOException {
        Path directory = directory(stored);
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static List<PreviewResponse.Page> pages(List<JsonNode> trees) {
        List<PreviewResponse.Page> pages = new ArrayList<>();
        for (int index = 0; index < trees.size(); index++) {
            JsonNode box = trees.get(index).path("bbox");
            pages.add(new PreviewResponse.Page(index + 1, box.path("w").asDouble(), box.path("h").asDouble()));
        }
        return pages;
    }

    private List<PreviewResponse.Hotspot> hotspots(StoredDocument stored, List<JsonNode> trees)
            throws HwpDocumentException {
        List<CellSnapshot> cells = HwpDocument.open(stored.source()).cells();
        Map<CellAddress, CellSnapshot> byAddress = cells.stream()
                .collect(Collectors.toMap(CellSnapshot::address, Function.identity()));
        Map<String, Integer> tableIndexes = matchTables(cells, trees);

        // (table, row, column) as the renderer reports them → boxes on each page.
        Map<List<Integer>, List<PreviewResponse.Hotspot>> boxes = new HashMap<>();
        for (int page = 0; page < trees.size(); page++) {
            collectCells(trees.get(page), page + 1, null, tableIndexes, boxes);
        }
        List<PreviewResponse.Hotspot> hotspots = new ArrayList<>();
        for (FieldCandidate field : stored.fields()) {
            CellSnapshot cell = byAddress.get(field.address());
            if (cell == null) {
                continue;
            }
            List<Integer> key = List.of(field.address().tableIndex(), cell.rowAddress(), cell.columnAddress());
            for (PreviewResponse.Hotspot box : boxes.getOrDefault(key, List.of())) {
                hotspots.add(new PreviewResponse.Hotspot(
                        field.id(), box.page(), box.x(), box.y(), box.width(), box.height()));
            }
        }
        return hotspots;
    }

    private static void collectCells(
            JsonNode node,
            int page,
            Integer table,
            Map<String, Integer> tableIndexes,
            Map<List<Integer>, List<PreviewResponse.Hotspot>> boxes) {
        String type = node.path("type").asString("");
        Integer current = table;
        if (type.equals("Table")) {
            // Nested tables are not fields of their own; only top-level tables are matched.
            current = table == null ? tableIndexes.getOrDefault(tableKey(node), -1) : -1;
        }
        if (type.equals("Cell") && current != null && current >= 0) {
            JsonNode box = node.path("bbox");
            boxes.computeIfAbsent(List.of(current, node.path("row").asInt(), node.path("col").asInt()),
                    ignored -> new ArrayList<>()).add(new PreviewResponse.Hotspot("", page,
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
        List<Integer> ours = new ArrayList<>(sizes.keySet());
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Map.Entry<String, int[]> table : rendered.entrySet()) {
            for (int index : ours) {
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

    private static Path completed(StoredDocument stored) {
        Path hwp = stored.directory().resolve("completed.hwp");
        if (!Files.exists(hwp)) {
            throw new DocumentStore.DocumentNotFoundException();
        }
        return hwp;
    }

    private static Path directory(StoredDocument stored) {
        return stored.directory().resolve("preview");
    }
}
