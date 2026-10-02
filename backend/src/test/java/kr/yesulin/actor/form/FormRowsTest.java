package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellWrite;
import kr.yesulin.actor.document.EditTarget;
import kr.yesulin.actor.document.JobContent;
import kr.yesulin.actor.document.TableGrowth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Rows items (출연 경력 표, 배우가 더하는 항목): parsing, answers, and where rows land in the document. */
class FormRowsTest {
    /**
     * Table 0: 이름 (row 0), 연락처 (row 1); the applicant's own items go between them.
     * Table 1: header (row 0), two career rows (1–2), 자기소개 (row 3).
     */
    static final String DEFINITION = """
            {
              "title": "줄 표 시험",
              "items": [
                { "id": "name", "label": "이름", "type": "text", "required": true },
                { "id": "phone", "label": "연락처", "type": "phone" },
                { "id": "career", "label": "출연 경력", "type": "rows", "maxRows": 5,
                  "columns": [ { "id": "title", "label": "작품명" }, { "id": "role", "label": "역할" },
                               { "id": "year", "label": "연도" } ] },
                { "id": "more", "label": "더 알리고 싶은 것", "type": "rows", "maxRows": 3,
                  "columns": [ { "id": "label", "label": "항목" }, { "id": "value", "label": "내용" } ] },
                { "id": "intro", "label": "자기소개", "type": "text", "multiline": true }
              ],
              "outputs": [
                { "cell": "0.0.1", "text": "{name}" },
                { "cell": "0.1.0", "rows": "more", "columns": ["label", "value"], "formRows": 0, "grow": true },
                { "cell": "0.1.1", "text": "{phone}" },
                { "cell": "1.1.0", "rows": "career", "columns": ["", "title", "role", "year"], "formRows": 2, "grow": true },
                { "cell": "1.3.1", "text": "{intro}" }
              ]
            }
            """;

    private final FormDefinitionParser parser = new FormDefinitionParser(JsonMapper.builder().build());
    private final FormDefinition definition = parser.parse(DEFINITION);

    @Test
    @DisplayName("줄 표 항목은 열 목록과 최대 줄 수를 읽는다")
    void parse_ReadsColumnsAndMaxRows_WhenItemIsRows() {
        FormItem career = definition.item("career").orElseThrow();

        assertThat(career.type()).isEqualTo(FormItem.Type.ROWS);
        assertThat(career.columns()).extracting(FormItem.Column::id).containsExactly("title", "role", "year");
        assertThat(career.maxRows()).isEqualTo(5);
    }

    @Test
    @DisplayName("양식 줄 안에 들어가면 줄을 더하지 않고 번호 칸은 건너뛴다")
    void compose_WritesFormRowsOnly_WhenEntriesFitTheForm() {
        // when
        JobContent composed = compose(Map.of("career", List.of("햄릿", "호레이쇼", "2025")));

        // then
        assertThat(composed.growths()).isEmpty();
        assertThat(composed.writes()).contains(
                new CellWrite.Replace(new CellAddress(1, 1, 1), "햄릿"),
                new CellWrite.Replace(new CellAddress(1, 1, 3), "2025"),
                new CellWrite.Replace(new CellAddress(1, 3, 1), "소개"));
    }

    @Test
    @DisplayName("양식 줄보다 많이 쓰면 마지막 줄 아래에 줄을 더하고 그 아래 칸을 밀어 쓴다")
    void compose_GrowsTableAndMovesCellsBelow_WhenEntriesRunPastTheForm() {
        // when
        JobContent composed = compose(Map.of("career", List.of(
                "가", "1", "2021", "나", "2", "2022", "다", "3", "2023", "라", "4", "2024")));

        // then
        assertThat(composed.growths()).containsExactly(new TableGrowth(1, 2, 2));
        assertThat(composed.writes()).contains(
                new CellWrite.Replace(new CellAddress(1, 4, 1), "라"),
                new CellWrite.Replace(new CellAddress(1, 5, 1), "소개"));
    }

    @Test
    @DisplayName("미리보기에서 누를 칸도 늘어난 줄을 따라가고, 새 줄도 줄 표 항목을 연다")
    void compose_MovesPreviewTargets_WhenRowsWereAdded() {
        // when
        JobContent composed = compose(Map.of("career", List.of(
                "가", "1", "2021", "나", "2", "2022", "다", "3", "2023", "라", "4", "2024")));

        // then
        assertThat(composed.targets()).contains(
                new EditTarget("career", new CellAddress(1, 4, 1)),
                new EditTarget("career", new CellAddress(1, 4, 3)),
                new EditTarget("intro", new CellAddress(1, 5, 1)));
        assertThat(composed.targets()).doesNotContain(
                new EditTarget("intro", new CellAddress(1, 3, 1)),
                new EditTarget("career", new CellAddress(1, 1, 0)));
    }

    @Test
    @DisplayName("줄을 줄여 다시 만들면 누를 칸도 양식 줄로 돌아온다")
    void compose_KeepsTargetsOnTheFormRows_WhenEntriesFitAgain() {
        // when
        JobContent composed = compose(Map.of("career", List.of("햄릿", "", "")));

        // then
        assertThat(composed.targets()).contains(
                new EditTarget("career", new CellAddress(1, 2, 2)),
                new EditTarget("intro", new CellAddress(1, 3, 1)));
        assertThat(composed.targets()).noneMatch(target -> target.address().tableIndex() == 1
                && target.address().rowIndex() == 4);
    }

    @Test
    @DisplayName("양식에 줄이 없는 항목은 바로 위 줄 아래에 줄을 만들고 원래 그 자리의 칸을 민다")
    void compose_AddsRowsUnderTheRowAbove_WhenOutputHasNoFormRows() {
        // when
        JobContent composed = compose(Map.of(
                "more", List.of("영상", "youtu.be/x", "수상", "신인상"), "phone", List.of("01012345678")));

        // then
        assertThat(composed.growths()).containsExactly(new TableGrowth(0, 0, 2));
        assertThat(composed.writes()).contains(
                new CellWrite.Replace(new CellAddress(0, 1, 0), "영상"),
                new CellWrite.Replace(new CellAddress(0, 2, 1), "신인상"),
                new CellWrite.Replace(new CellAddress(0, 3, 1), "010-1234-5678"));
        assertThat(composed.targets()).contains(
                new EditTarget("more", new CellAddress(0, 1, 0)),
                new EditTarget("more", new CellAddress(0, 2, 1)),
                new EditTarget("phone", new CellAddress(0, 3, 1)));
    }

    @Test
    @DisplayName("빈 줄은 버리고 줄 수를 넘기면 거절한다")
    void answers_DropsBlankRowsAndRejectsTooMany() {
        FormAnswers answers = answers(Map.of("career", List.of("", " ", "", "햄릿", "", "")));
        assertThat(answers.rows("career")).containsExactly(List.of("햄릿", "", ""));

        List<String> six = java.util.Collections.nCopies(18, "x");
        assertThatThrownBy(() -> answers(Map.of("career", six)))
                .isInstanceOf(InvalidAnswerException.class).hasMessage("출연 경력 항목은 5줄까지 쓸 수 있어요");
    }

    @Test
    @DisplayName("열 수로 나눠지지 않는 값은 잘못된 요청이다")
    void answers_Rejects_WhenValuesDoNotFillWholeRows() {
        assertThatThrownBy(() -> answers(Map.of("career", List.of("햄릿", "호레이쇼"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("줄 표 출력의 잘못된 열, 늘릴 수 없는 줄 수, 글자 템플릿 사용을 알려준다")
    void parse_ListsRowsProblems() {
        String wrong = DEFINITION
                .replace("[\"\", \"title\", \"role\", \"year\"], \"formRows\": 2, \"grow\": true",
                        "[\"title\", \"venue\"], \"formRows\": 2")
                .replace("\"text\": \"{intro}\"", "\"text\": \"{intro} {career}\"");

        assertThatThrownBy(() -> parser.parse(wrong))
                .isInstanceOfSatisfying(InvalidFormDefinitionException.class, invalid -> assertThat(invalid.problems())
                        .anyMatch(problem -> problem.contains("'venue'"))
                        .anyMatch(problem -> problem.contains("grow") && problem.contains("maxRows(5)"))
                        .anyMatch(problem -> problem.contains("{career}")));
    }

    private JobContent compose(Map<String, List<String>> extra) {
        return FormComposer.compose(definition, answers(extra), Map.of());
    }

    private FormAnswers answers(Map<String, List<String>> extra) {
        Map<String, List<String>> values = new HashMap<>(Map.of("name", List.of("홍길동"), "intro", List.of("소개")));
        values.putAll(extra);
        return FormAnswers.of(definition, values, Map.of());
    }
}
