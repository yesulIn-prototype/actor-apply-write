package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.yesulin.actor.document.CellAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FormDefinitionParserTest {
    private final FormDefinitionParser parser = new FormDefinitionParser(JsonMapper.builder().build());

    @Test
    @DisplayName("운영자가 쓴 정의를 화면 항목과 문서 출력으로 나눠 읽는다")
    void parse_ReadsItemsAndOutputs_WhenDefinitionIsValid() throws IOException {
        // when
        FormDefinition definition = parser.parse(sampleDefinition());

        // then
        assertThat(definition.items()).extracting(FormItem::id).startsWith("name", "birth", "phone");
        FormItem role = definition.item("role").orElseThrow();
        assertThat(role.type()).isEqualTo(FormItem.Type.MULTI);
        assertThat(role.options()).extracting(FormItem.Option::written).containsExactly("곰역", "공주역");
        assertThat(definition.outputs()).contains(
                new FormOutput.Text(new CellAddress(0, 7, 1), "{guardianName} / {guardianPhone}", ", "),
                new FormOutput.Photo(new CellAddress(0, 0, 4), "photo"));
    }

    @Test
    @DisplayName("제출 안내는 메일 제목에 쓴 항목도 쓰인 것으로 보고, 주소·날짜 형식과 없는 항목을 알린다")
    void parse_ReadsSubmission_AndChecksItsFormat() throws IOException {
        String withSubmission = sampleDefinition().replace("\"items\": [", """
                "submission": { "email": "audition@example.com", "subject": "숲속_{role}_{name}",
                                "deadline": "2026-10-15", "note": "영상 링크 함께" },
                "items": [""");
        FormDefinition definition = parser.parse(withSubmission);
        assertThat(definition.submission()).isEqualTo(
                new Submission("audition@example.com", "숲속_{role}_{name}", "2026-10-15", "영상 링크 함께"));
        assertThat(parser.parse(sampleDefinition()).submission()).isEqualTo(Submission.NONE);

        String wrong = withSubmission.replace("audition@example.com", "audition")
                .replace("2026-10-15", "10월 15일").replace("{role}", "{nobody}");
        assertThatThrownBy(() -> parser.parse(wrong))
                .isInstanceOfSatisfying(InvalidFormDefinitionException.class, invalid -> assertThat(invalid.problems())
                        .anyMatch(problem -> problem.startsWith("submission.email"))
                        .anyMatch(problem -> problem.startsWith("submission.deadline"))
                        .anyMatch(problem -> problem.contains("{nobody}")));
    }

    @Test
    @DisplayName("타입에 맞지 않는 설정과 잘못된 참조를 한 번에 모두 알려준다")
    void parse_ListsEveryProblem_WhenDefinitionIsWrong() {
        // given
        String definition = """
                {
                  "title": "테스트",
                  "items": [
                    { "id": "name", "label": "이름", "type": "text", "options": [] },
                    { "id": "gender", "label": "성별", "type": "single", "options": [{ "id": "m", "label": "남" }] },
                    { "id": "photo", "label": "사진", "type": "photo", "mutliline": true },
                    { "id": "unused", "label": "안 쓰는 칸", "type": "text" }
                  ],
                  "outputs": [
                    { "cell": "0.0.1", "text": "{name} {nobody}" },
                    { "cell": "0.0.1", "text": "{name}" },
                    { "cell": "0.1.1", "text": "{photo}" },
                    { "cell": "0.2.1", "edit": [{ "find": "남(  )", "replace": "남( V )", "when": "gender=x" }] },
                    { "cell": "1-2-3", "text": "{name}" }
                  ]
                }
                """;

        // when / then
        assertThatThrownBy(() -> parser.parse(definition))
                .isInstanceOfSatisfying(InvalidFormDefinitionException.class, invalid -> assertThat(invalid.problems())
                        .anyMatch(problem -> problem.contains("(name)") && problem.contains("options"))
                        .anyMatch(problem -> problem.contains("(gender)") && problem.contains("2개 이상"))
                        .anyMatch(problem -> problem.contains("mutliline"))
                        .anyMatch(problem -> problem.contains("{nobody}"))
                        .anyMatch(problem -> problem.contains("두 번"))
                        .anyMatch(problem -> problem.contains("사진 항목 {photo}"))
                        .anyMatch(problem -> problem.contains("gender=x"))
                        .anyMatch(problem -> problem.contains("1-2-3"))
                        .anyMatch(problem -> problem.contains("(unused)") && problem.contains("쓰이지 않습니다")));
    }

    @Test
    @DisplayName("JSON 문법이 틀리면 형식 오류 하나로 알려준다")
    void parse_ReportsSyntax_WhenJsonIsMalformed() {
        assertThatThrownBy(() -> parser.parse("{ \"title\": "))
                .isInstanceOfSatisfying(InvalidFormDefinitionException.class,
                        invalid -> assertThat(invalid.problems()).singleElement().asString().startsWith("JSON 형식 오류"));
    }

    @Test
    @DisplayName("원본에 없는 칸과 원문에 없는 찾을 글자를 알려준다")
    void sourceCheck_ListsMissingCellsAndText_WhenDefinitionDoesNotMatchForm() throws IOException {
        // given
        FormDefinition definition = parser.parse(sampleDefinition());
        var cells = List.of(
                new kr.yesulin.actor.document.CellSnapshot(new CellAddress(0, 2, 1), 1, 2, 1, 1, 100, 100, "남 / 여"));

        // when
        List<String> problems = SourceCheck.problems(definition, cells);

        // then
        assertThat(problems)
                .anyMatch(problem -> problem.contains("0.0.1") && problem.contains("칸이 없습니다"))
                .anyMatch(problem -> problem.contains("'남(  )'가 없습니다"));
    }

    static String sampleDefinition() throws IOException {
        try (InputStream input = FormDefinitionParserTest.class.getResourceAsStream("/forms/sample-notice.definition.json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
