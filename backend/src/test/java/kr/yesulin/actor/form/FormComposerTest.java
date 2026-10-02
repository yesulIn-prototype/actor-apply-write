package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellWrite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

class FormComposerTest {
    private static final Map<CellAddress, String> BLANK_FORM = Map.of(
            new CellAddress(0, 2, 1), "남(  )  여(  )",
            new CellAddress(0, 6, 1), "□곰역 및 다역  □공주 및 다역");
    private static final MockMultipartFile PHOTO = new MockMultipartFile("photo-photo", "me.png", "image/png", new byte[] {1});

    private final FormDefinition definition;

    FormComposerTest() throws IOException {
        definition = new FormDefinitionParser(JsonMapper.builder().build()).parse(FormDefinitionParserTest.sampleDefinition());
    }

    @Test
    @DisplayName("화면에서 따로 받은 값을 문서의 한 칸에 정한 형식으로 합친다")
    void compose_JoinsSeveralAnswersIntoOneCell_WhenTemplateNamesThem() {
        // when
        List<CellWrite> writes = compose(answers(Map.of(
                "guardianName", List.of("홍부모"), "guardianPhone", List.of("0212345678"))));

        // then
        assertThat(writes).contains(new CellWrite.Replace(new CellAddress(0, 7, 1), "홍부모 / 02-1234-5678"));
    }

    @Test
    @DisplayName("선택 결과를 원문의 괄호와 네모에 표시한다")
    void compose_MarksPickedOptionsInTheFormsOwnText_WhenEditRulesMatch() {
        // when
        List<CellWrite> writes = compose(answers(Map.of("gender", List.of("f"), "role", List.of("princess"))));

        // then
        assertThat(writes).contains(
                new CellWrite.Replace(new CellAddress(0, 2, 1), "남(  )  여( V )"),
                new CellWrite.Replace(new CellAddress(0, 6, 1), "□곰역 및 다역  ■공주 및 다역"));
    }

    @Test
    @DisplayName("답하지 않은 항목의 칸은 원본 그대로 둔다")
    void compose_LeavesCellsAlone_WhenTheirAnswersAreMissing() {
        // when
        List<CellWrite> writes = compose(answers(Map.of()));

        // then
        assertThat(writes).extracting(CellWrite::address).containsExactlyInAnyOrder(
                new CellAddress(0, 0, 1), new CellAddress(0, 0, 3), new CellAddress(0, 1, 1),
                new CellAddress(0, 2, 1), new CellAddress(0, 6, 1), new CellAddress(0, 0, 4));
    }

    @Test
    @DisplayName("다중 선택은 문서용 표기로, 파일 이름에서는 밑줄로 잇는다")
    void fileName_JoinsPicksWithUnderscore_WhenSeveralOptionsArePicked() {
        // given
        FormAnswers answers = answers(Map.of("role", List.of("bear", "princess")));

        // when
        String fileName = FormComposer.fileName(definition, answers);

        // then
        assertThat(fileName).isEqualTo("홍길동_곰역_공주역_지원서");
        assertThat(answers.written("role", ", ")).isEqualTo("곰역, 공주역");
    }

    @Test
    @DisplayName("필수 항목이 비면 배우에게 보일 안내로 거절한다")
    void answers_RejectsWithApplicantMessage_WhenRequiredItemIsMissing() {
        Map<String, List<String>> values = new HashMap<>(required());
        values.remove("phone");
        assertThatThrownBy(() -> FormAnswers.of(definition, values, Map.of("photo", PHOTO)))
                .isInstanceOf(InvalidAnswerException.class).hasMessage("연락처 항목을 입력해주세요");
    }

    @Test
    @DisplayName("전화번호가 아니면 거절한다")
    void answers_RejectsPhone_WhenDigitsAreNotAPhoneNumber() {
        Map<String, List<String>> values = new HashMap<>(required());
        values.put("phone", List.of("12345"));
        assertThatThrownBy(() -> FormAnswers.of(definition, values, Map.of("photo", PHOTO)))
                .isInstanceOf(InvalidAnswerException.class).hasMessage("연락처 번호를 확인해주세요");
    }

    @Test
    @DisplayName("정의에 없는 선택지는 받지 않는다")
    void answers_RejectsOption_WhenNotDefined() {
        Map<String, List<String>> values = new HashMap<>(required());
        values.put("gender", List.of("x"));
        assertThatThrownBy(() -> FormAnswers.of(definition, values, Map.of("photo", PHOTO)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gender=x");
    }

    @Test
    @DisplayName("필수 사진이 없으면 거절한다")
    void answers_RejectsWithApplicantMessage_WhenRequiredPhotoIsMissing() {
        assertThatThrownBy(() -> FormAnswers.of(definition, required(), Map.of()))
                .isInstanceOf(InvalidAnswerException.class).hasMessage("프로필 사진을 넣어주세요");
    }

    private List<CellWrite> compose(FormAnswers answers) {
        return FormComposer.compose(definition, answers, BLANK_FORM).writes();
    }

    private FormAnswers answers(Map<String, List<String>> extra) {
        Map<String, List<String>> values = new HashMap<>(required());
        values.putAll(extra);
        Map<String, MultipartFile> photos = Map.of("photo", PHOTO);
        return FormAnswers.of(definition, values, photos);
    }

    private static Map<String, List<String>> required() {
        return Map.of(
                "name", List.of("홍길동"),
                "birth", List.of("1996.03.01"),
                "phone", List.of("010 1234 5678"),
                "gender", List.of("m"),
                "role", List.of("bear"));
    }
}
