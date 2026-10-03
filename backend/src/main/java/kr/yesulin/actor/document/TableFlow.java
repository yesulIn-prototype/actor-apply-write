package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps rhwp's PDF from drawing a table over the one above it. When a table no longer fits on its page (pushed
 * down by added rows or long answers), rhwp does not carry it to the next page; it squeezes it up over what is
 * there. So after the file is written, rhwp's page plan is read, and a page that runs over gets its last whole
 * table, with the short heading right above it, moved to a new page through that paragraph's own page break.
 * Hangul lays pages out itself; the break only spells out what it would do anyway. rhwp only reads here.
 */
final class TableFlow {
    /** Each break can push a later table over its page in turn; a form has only a few tables. */
    private static final int MOST_BREAKS = 3;
    private static final int HEADING_LONGEST = 40;
    /** Rounding in rhwp's pixel heights. */
    private static final double SLACK = 0.5;

    private final PdfConverter rhwp;
    private final JsonMapper json;

    TableFlow(PdfConverter rhwp, JsonMapper json) {
        this.rhwp = rhwp;
        this.json = json;
    }

    record Break(int section, int paragraph) {}

    /** @param file {@code document} as saved; saved again after each break */
    void keepTablesApart(HwpDocument document, Path file) throws IOException, HwpDocumentException {
        if (!rhwp.available()) {
            return;
        }
        for (int done = 0; done < MOST_BREAKS; done++) {
            Optional<Break> needed = needed(json.readTree(rhwp.pages(file)));
            if (needed.isEmpty() || !document.breakPageBefore(needed.get().section(), needed.get().paragraph())) {
                return;
            }
            document.save(file);
        }
    }

    /** The first page that runs over and has a whole table below something else: where its new page starts. */
    static Optional<Break> needed(JsonNode plan) {
        for (JsonNode page : plan.path("pages")) {
            double room = page.path("bodyArea").path("height").asDouble();
            for (JsonNode column : page.path("columns")) {
                if (column.path("usedHeight").asDouble() <= room + SLACK) {
                    continue;
                }
                List<JsonNode> items = new ArrayList<>();
                column.path("items").forEach(items::add);
                int table = lastWholeTable(items);
                if (table <= 0) {
                    continue;
                }
                int start = heading(items.get(table - 1)) && table - 1 > 0 ? table - 1 : table;
                return Optional.of(new Break(page.path("section").asInt(), items.get(start).path("paraIndex").asInt()));
            }
        }
        return Optional.empty();
    }

    /** A table rhwp split over pages ("partialTable") already flows; only a whole one gets stuck. */
    private static int lastWholeTable(List<JsonNode> items) {
        for (int at = items.size() - 1; at >= 0; at--) {
            if (items.get(at).path("kind").asString().equals("table")) {
                return at;
            }
        }
        return -1;
    }

    /** A short line of text right above a table is its title ("4. 프로필 사진"): it moves with the table. */
    private static boolean heading(JsonNode item) {
        String text = item.path("textPreview").asString().strip();
        return item.path("kind").asString().equals("fullParagraph") && !text.isEmpty() && text.length() <= HEADING_LONGEST;
    }
}
