package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellSnapshot;
import kr.yesulin.actor.document.CellWrite;
import kr.yesulin.actor.document.JobContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Rows items whose rows are fixed by the form (학력: 고등학교·대학교·대학원 줄마다 라벨). */
class FormLabeledRowsTest {
    /** Table 0: 이름 (row 0), then one row per school level with "(        ) 고등학교" in its first cell. */
    static final String DEFINITION = """
            {
              "title": "줄 라벨 시험",
              "items": [
                { "id": "name", "label": "이름", "type": "text", "required": true },
                { "id": "school", "label": "학력", "type": "rows", "rowLabels": ["고등학교", "대학교", "대학원"],
                  "columns": [ { "id": "name", "label": "학교명" }, { "id": "region", "label": "지역" },
                               { "id": "year", "label": "졸업년도" } ] }
              ],
              "outputs": [
                { "cell": "0.0.1", "text": "{name}" },
                { "cell": "0.1.0", "rows": "school", "formRows": 3,
                  "columns": [ { "id": "name", "find": "(        )", "replace": "( {name} )" }, "region", "year" ] }
              ]
            }
            """;

    private static final Map<CellAddress, String> FORM = Map.of(
            new CellAddress(0, 1, 0), "(        ) 고등학교",
            new CellAddress(0, 2, 0), "(        )   대학교",
            new CellAddress(0, 3, 0), "(        )   대학원");

    private final FormDefinitionParser parser = new FormDefinitionParser(JsonMapper.builder().build());
    private final FormDefinition definition = parser.parse(DEFINITION);

    @Test
    @DisplayName("줄 라벨이 있으면 라벨 수가 곧 줄 수다")
    void parse_TakesMaxRowsFromLabels_WhenRowsAreLabeled() {
        FormItem school = definition.item("school").orElseThrow();

        assertThat(school.rowLabels()).containsExactly("고등학교", "대학교", "대학원");
        assertThat(school.maxRows()).isEqualTo(3);
    }

    @Test
    @DisplayName("라벨 줄은 빈 줄도 자리를 지켜 다음 줄이 위로 올라오지 않는다")
    void answers_KeepsBlankRowsInPlace_WhenRowsAreLabeled() {
        FormAnswers answers = answers(List.of("", "", "", "시험대", "서울", "2023", "", "", ""));

        assertThat(answers.rows("school")).containsExactly(
                List.of("", "", ""), List.of("시험대", "서울", "2023"), List.of("", "", ""));
    }

    @Test
    @DisplayName("라벨 줄이 모두 비면 답하지 않은 것이다")
    void answers_TreatsAllBlankAsUnanswered_WhenRowsAreLabeled() {
        assertThat(answers(List.of("", "", "", " ", "", "", "", "", "")).answered("school")).isFalse();
    }

    @Test
    @DisplayName("쓴 줄만 제 라벨 줄에 쓰고, 라벨 칸은 괄호만 채운다")
    void compose_WritesOnlyAnsweredRows_KeepingTheLabel() {
        // when
        JobContent composed = FormComposer.compose(
                definition, answers(List.of("", "", "", "시험대", "서울", "2023", "", "", "")), FORM);

        // then
        assertThat(composed.writes()).contains(
                new CellWrite.Replace(new CellAddress(0, 2, 0), "( 시험대 )   대학교"),
                new CellWrite.Replace(new CellAddress(0, 2, 1), "서울"),
                new CellWrite.Replace(new CellAddress(0, 2, 2), "2023"));
        assertThat(composed.writes()).noneMatch(write -> write.address().rowIndex() == 1 || write.address().rowIndex() == 3);
        assertThat(composed.growths()).isEmpty();
    }

    @Test
    @DisplayName("라벨 수와 다른 양식 줄, grow, 다른 maxRows, 열을 쓰지 않는 replace를 알려준다")
    void parse_ListsLabeledRowsProblems() {
        String wrong = DEFINITION
                .replace("\"rowLabels\": [", "\"maxRows\": 5, \"rowLabels\": [")
                .replace("\"formRows\": 3", "\"formRows\": 2, \"grow\": true")
                .replace("\"replace\": \"( {name} )\"", "\"replace\": \"( )\"");

        assertThatThrownBy(() -> parser.parse(wrong))
                .isInstanceOfSatisfying(InvalidFormDefinitionException.class, invalid -> assertThat(invalid.problems())
                        .anyMatch(problem -> problem.contains("maxRows") && problem.contains("rowLabels"))
                        .anyMatch(problem -> problem.contains("formRows") && problem.contains("grow"))
                        .anyMatch(problem -> problem.contains("{name}")));
    }

    @Test
    @DisplayName("라벨 칸의 찾을 글자가 어느 줄 원문에 없으면 알려준다")
    void sourceCheck_ListsRowsWhoseLabelCellLacksTheFind() {
        // given
        List<CellSnapshot> cells = new java.util.ArrayList<>();
        cells.add(snapshot(new CellAddress(0, 0, 1), ""));
        FORM.forEach((address, text) -> cells.add(snapshot(address, address.rowIndex() == 3 ? "(  ) 대학원" : text)));
        for (int row = 1; row <= 3; row++) {
            cells.add(snapshot(new CellAddress(0, row, 1), ""));
            cells.add(snapshot(new CellAddress(0, row, 2), ""));
        }

        // when
        List<String> problems = SourceCheck.problems(definition, cells);

        // then
        assertThat(problems).singleElement().asString().contains("0.3.0").contains("'(        )'");
    }

    private static CellSnapshot snapshot(CellAddress address, String text) {
        return new CellSnapshot(address, address.cellIndex(), address.rowIndex(), 1, 1, 100, 100, text);
    }

    private FormAnswers answers(List<String> school) {
        Map<String, List<String>> values = new HashMap<>(Map.of("name", List.of("홍길동")));
        values.put("school", school);
        return FormAnswers.of(definition, values, Map.of());
    }
}
