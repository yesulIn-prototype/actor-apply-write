package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Reading rhwp's page plan (dump-pages --json) for a table that no longer fits on its page. */
class TableFlowTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("넘친 쪽의 마지막 표는 바로 위 제목과 함께 새 쪽에서 시작한다")
    void needed_BreaksBeforeTheHeadingOfTheLastTable_WhenAPageRunsOver() {
        Optional<TableFlow.Break> needed = TableFlow.needed(JSON.readTree(plan(1210,
                paragraph(7, "3. 출연 경력 (최근순)"), table(8), paragraph(9, ""), paragraph(10, "4. 프로필 사진"), table(11))));

        assertThat(needed).contains(new TableFlow.Break(0, 10));
    }

    @Test
    @DisplayName("표 바로 위가 빈 줄이면 표부터 새 쪽에서 시작한다")
    void needed_BreaksBeforeTheTable_WhenNoHeadingSitsAboveIt() {
        Optional<TableFlow.Break> needed = TableFlow.needed(JSON.readTree(plan(1000,
                table(3), paragraph(4, ""), table(5))));

        assertThat(needed).contains(new TableFlow.Break(0, 5));
    }

    @Test
    @DisplayName("쪽 안에 들어가거나, 나뉘어 이어지는 표만 넘치거나, 표가 쪽 맨 위면 그대로 둔다")
    void needed_LeavesThePageAlone_WhenMovingATableCannotHelp() {
        assertThat(TableFlow.needed(JSON.readTree(plan(863, paragraph(7, "3. 출연 경력"), table(8), table(11))))).isEmpty();
        assertThat(TableFlow.needed(JSON.readTree(plan(1000, partialTable(6))))).isEmpty();
        assertThat(TableFlow.needed(JSON.readTree(plan(1000, table(8), paragraph(9, ""))))).isEmpty();
    }

    /** One page, 876.8px of room, as rhwp writes it. */
    private static String plan(double used, String... items) {
        return """
                {"pageCount": 1, "pages": [{"pageNumber": 1, "section": 0, "bodyArea": {"height": 876.84},
                  "columns": [{"index": 0, "usedHeight": %s, "items": [%s]}]}]}
                """.formatted(used, String.join(",", items));
    }

    private static String paragraph(int index, String text) {
        return "{\"kind\": \"fullParagraph\", \"paraIndex\": %d, \"textPreview\": \"%s\"}".formatted(index, text);
    }

    private static String table(int index) {
        return "{\"kind\": \"table\", \"paraIndex\": %d}".formatted(index);
    }

    private static String partialTable(int index) {
        return "{\"kind\": \"partialTable\", \"paraIndex\": %d}".formatted(index);
    }
}
