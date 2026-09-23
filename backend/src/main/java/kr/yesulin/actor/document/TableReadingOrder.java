package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Orders fields the way the form reads on paper. A floating table can come first in the file yet sit
 * lower on the page ("작품경력" anchored above "성명" and pushed down 11 cm), so the stored table order
 * is not the reading order; the rendered page position is.
 */
final class TableReadingOrder {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TableReadingOrder() {}

    /** Fields sorted by where their table lands (page, then top edge); file order when rendering is unavailable. */
    static List<FieldCandidate> sort(
            List<FieldCandidate> fields, List<CellSnapshot> cells, PdfConverter renderer, Path source) {
        Map<Integer, Integer> rank = rank(cells, renderer, source);
        if (rank.isEmpty()) {
            return fields;
        }
        return fields.stream()
                .sorted(Comparator.comparingInt((FieldCandidate field) ->
                                rank.getOrDefault(field.address().tableIndex(), Integer.MAX_VALUE))
                        .thenComparingInt(field -> field.address().tableIndex())
                        .thenComparingInt(field -> field.address().rowIndex())
                        .thenComparingInt(field -> field.address().cellIndex()))
                .toList();
    }

    private static Map<Integer, Integer> rank(List<CellSnapshot> cells, PdfConverter renderer, Path source) {
        if (!renderer.available()) {
            return Map.of();
        }
        Path layout = source.resolveSibling("analysis-layout");
        try {
            renderer.exportLayout(source, layout);
            List<JsonNode> trees = new ArrayList<>();
            try (Stream<Path> files = Files.list(layout)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                    trees.add(JSON.readTree(file.toFile()));
                }
            }
            List<PlacedTable> placed = new ArrayList<>();
            for (int page = 0; page < trees.size(); page++) {
                collect(trees.get(page), page, false, placed);
            }
            Map<String, Integer> matched = PreviewService.matchTables(cells, trees);
            placed.sort(Comparator.comparingInt(PlacedTable::page).thenComparingDouble(PlacedTable::top));
            Map<Integer, Integer> rank = new HashMap<>();
            for (PlacedTable table : placed) {
                Integer index = matched.get(table.key());
                if (index != null) {
                    rank.putIfAbsent(index, rank.size());
                }
            }
            return rank;
        } catch (IOException | HwpDocumentException | RuntimeException exception) {
            // Reading order is a nicety; the form still works in file order.
            return Map.of();
        } finally {
            deleteQuietly(layout);
        }
    }

    private static void collect(JsonNode node, int page, boolean insideTable, List<PlacedTable> tables) {
        boolean table = node.path("type").asString("").equals("Table");
        if (table && !insideTable) {
            tables.add(new PlacedTable(PreviewService.tableKey(node), page, node.path("bbox").path("y").asDouble()));
        }
        for (JsonNode child : node.path("children")) {
            collect(child, page, insideTable || table, tables);
        }
    }

    private static void deleteQuietly(Path directory) {
        try {
            List<Path> files;
            try (Stream<Path> listing = Files.list(directory)) {
                files = listing.toList();
            }
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // Left for the document store's expiry sweep.
        }
    }

    private record PlacedTable(String key, int page, double top) {}
}
