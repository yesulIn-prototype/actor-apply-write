package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellSnapshot;
import kr.yesulin.actor.document.HwpDocument;
import kr.yesulin.actor.document.PdfConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/** A notice on the standard form, written as settings ("base": "standard-v1") and turned into its own form. */
@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-notice-test/work",
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-actor-notice-test/forms-${random.uuid}",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-notice-test/completed-count.txt",
        "yesulin.admin-token=" + NoticeFormHttpAcceptanceTest.TOKEN})
@AutoConfigureMockMvc
class StandardNoticeHttpAcceptanceTest {
    static final String SPEC = """
            {
              "base": "standard-v1",
              "title": "가족 뮤지컬 〈별빛 정원〉 배우 지원서",
              "fileName": "별빛정원_{role}_{name}",
              "roles": ["별지기", "정원사", "고양이"],
              "drop": ["current"],
              "extras": ["video", { "use": "auditionDates", "options": ["10월 9일", "10월 10일"] },
                         { "custom": { "id": "puppet", "label": "인형극 경험", "type": "yesno" }, "required": true }],
              "help": { "photoMain": "최근 6개월 이내 사진" },
              "submission": { "email": "audition@example.com", "subject": "별빛정원_{role}_{name}",
                              "deadline": "2026-10-15", "note": "자유곡 영상 링크를 함께 보내주세요" }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PdfConverter rhwp;

    @BeforeEach
    void needsRhwp() {
        assumeTrue(rhwp.available(), "rhwp not installed — run tools/install-rhwp.sh");
    }

    @Test
    @DisplayName("공고 설정을 저장하면 서버가 그 공고의 지원서 파일과 정의를 만들고, 운영자에게는 쓴 설정을 그대로 보인다")
    void saveSettings_MakesTheNoticesOwnForm() throws Exception {
        save("60001", SPEC).andExpect(status().isOk())
                .andExpect(jsonPath("$.problems").isEmpty())
                .andExpect(jsonPath("$.standard").value(true))
                .andExpect(jsonPath("$.hasSource").value(true))
                .andExpect(jsonPath("$.definition").value(SPEC))
                .andExpect(jsonPath("$.cells[?(@.address == '1.0.0')].text").value("지원 배역"))
                .andExpect(jsonPath("$.cells[?(@.address == '1.1.0')].text").value("참여 불가 일정"))
                .andExpect(jsonPath("$.cells[?(@.address == '1.2.0')].text").value("영상 링크"))
                .andExpect(jsonPath("$.cells[?(@.address == '1.4.0')].text").value("인형극 경험"))
                .andExpect(jsonPath("$.cells[?(@.address == '1.5.0')].text").value("자기소개·지원동기"));

        mockMvc.perform(admin(get("/api/admin/forms/60001/form")))
                .andExpect(jsonPath("$.items[?(@.id == 'role')].type").value("SINGLE"))
                .andExpect(jsonPath("$.items[?(@.id == 'role')].options[*].label", hasItem("정원사")))
                .andExpect(jsonPath("$.items[?(@.id == 'puppet')].required").value(true))
                .andExpect(jsonPath("$.items[?(@.id == 'photoMain')].help").value("최근 6개월 이내 사진"))
                .andExpect(jsonPath("$.submission.email").value("audition@example.com"))
                .andExpect(jsonPath("$.pdfFirst").value(true));
    }

    @Test
    @DisplayName("운영자 화면은 추가 항목 목록을 받아 체크 목록으로 보인다")
    void catalog_ListsTheExtrasAnOperatorCanAdd() throws Exception {
        mockMvc.perform(admin(get("/api/admin/standard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("standard-v1"))
                .andExpect(jsonPath("$.extras[0].key").value("video"))
                .andExpect(jsonPath("$.extras[?(@.key == 'auditionDates')].operatorOptions").value(true))
                .andExpect(jsonPath("$.extras[?(@.key == 'tour')].type").value("single"));
        mockMvc.perform(get("/api/admin/standard")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만든 지원서에 고른 배역과 추가 항목이 그 줄에, 공고 제목이 제목 줄에 들어간다")
    void testBuild_FillsTheRowsTheSettingsAdded() throws Exception {
        // given
        save("60002", SPEC).andExpect(jsonPath("$.problems").isEmpty());
        String answers = """
                {"version": 0, "fileName": "", "answers": {
                  "name": ["김배우"], "birth": ["1998.03.01"], "phone": ["01012345678"], "gender": ["f"],
                  "role": ["o2"], "video": ["자유연기 youtu.be/example"], "auditionDates": ["o1", "o2"],
                  "puppet": ["yes"], "intro": ["무대가 좋습니다"]}}
                """;

        // when
        byte[] built = mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/test", "60002")
                        .file(new MockMultipartFile("request", "request.json", "application/json",
                                answers.getBytes(StandardCharsets.UTF_8)))
                        .file(photo())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // then
        Map<CellAddress, String> cells = cells(built);
        assertThat(cells.get(new CellAddress(1, 0, 1))).isEqualTo("정원사");
        assertThat(cells.get(new CellAddress(1, 2, 1))).isEqualTo("자유연기 youtu.be/example");
        assertThat(cells.get(new CellAddress(1, 3, 1))).isEqualTo("10월 9일, 10월 10일");
        assertThat(cells.get(new CellAddress(1, 4, 1))).isEqualTo("예");
        assertThat(cells.get(new CellAddress(1, 5, 1))).isEqualTo("무대가 좋습니다");
        Path file = Files.createTempFile("standard-notice-", ".hwp");
        try {
            Files.write(file, built);
            assertThat(rhwp.pages(file)).contains("가족 뮤지컬").doesNotContain("{공고 제목}");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("공고 설정이 틀리면 저장하지 않고 고칠 곳을 모두 알린다")
    void saveSettings_ListsEveryProblem_WhenTheSettingsAreWrong() throws Exception {
        String wrong = """
                { "base": "standard-v2", "title": "", "roles": ["혼자"], "drop": ["role", "current", "unavailable", "intro"],
                  "extras": ["unknown", { "use": "auditionDates" }] }
                """;

        save("60003", wrong).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FORM_NOT_READY"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("standard-v1"),
                        org.hamcrest.Matchers.containsString("title"),
                        org.hamcrest.Matchers.containsString("배역이 하나뿐"),
                        org.hamcrest.Matchers.containsString("'intro'"),
                        org.hamcrest.Matchers.containsString("'unknown'"),
                        org.hamcrest.Matchers.containsString("선택지(options)를 2개 이상"))));
        mockMvc.perform(admin(get("/api/admin/forms/60003"))).andExpect(jsonPath("$.hasSource").value(false));
    }

    private ResultActions save(String vid, String settings) throws Exception {
        return mockMvc.perform(admin(put("/api/admin/forms/{vid}/definition", vid))
                .contentType(MediaType.APPLICATION_JSON).content(settings));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B admin(B request) {
        return request.header("Authorization", "Bearer " + NoticeFormHttpAcceptanceTest.TOKEN);
    }

    private static Map<CellAddress, String> cells(byte[] hwp) throws Exception {
        Path file = Files.createTempFile("standard-notice-", ".hwp");
        try {
            Files.write(file, hwp);
            return HwpDocument.open(file).cells().stream()
                    .collect(Collectors.toMap(CellSnapshot::address, CellSnapshot::text));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** A plain drawn square: no real person's photo goes into the repository. */
    private static MockMultipartFile photo() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB), "png", png);
        return new MockMultipartFile("photo-photoMain", "portrait.png", "image/png", png.toByteArray());
    }
}
