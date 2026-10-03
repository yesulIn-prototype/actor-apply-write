package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellSnapshot;
import kr.yesulin.actor.document.HwpDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-form-test/work",
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-actor-form-test/forms-${random.uuid}",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-form-test/completed-count.txt",
        "yesulin.admin-token=" + NoticeFormHttpAcceptanceTest.TOKEN})
@AutoConfigureMockMvc
class NoticeFormHttpAcceptanceTest {
    static final String TOKEN = "test-operator-token";
    private static final CellAddress NAME = new CellAddress(0, 0, 1);
    private static final CellAddress GENDER = new CellAddress(0, 2, 1);
    private static final CellAddress GUARDIAN = new CellAddress(0, 7, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FormStore store;

    @Test
    @DisplayName("공고 링크와 운영자 페이지 주소는 앱 화면을 돌려준다")
    void appRoutes_ForwardToTheApp_WhenNoticeOrOperatorAddressIsOpened() throws Exception {
        mockMvc.perform(get("/apply/22382")).andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/admin/forms/22382")).andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/apply/not-a-number")).andExpect(forwardedUrl(null));
    }

    @Test
    @DisplayName("운영자 API는 토큰 없이 열리지 않는다")
    void adminApi_Rejects_WhenTokenIsMissingOrWrong() throws Exception {
        mockMvc.perform(get("/api/admin/forms")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/forms").header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("검증 전에는 공개되지 않고, 공개 후 같은 링크의 두 배우 결과는 서로 섞이지 않는다")
    void noticeLink_KeepsApplicantsApart_WhenTwoApplicantsUseTheSameVid() throws Exception {
        // given: the operator registers the form, but has not test-built it yet
        Vid vid = new Vid("22382");
        register(vid);
        mockMvc.perform(admin(post("/api/admin/forms/22382/publish")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FORM_NOT_READY"));
        mockMvc.perform(get("/api/forms/22382")).andExpect(status().isNotFound());
        testBuild();
        mockMvc.perform(admin(post("/api/admin/forms/22382/publish"))).andExpect(jsonPath("$.status").value("PUBLISHED"));
        byte[] sharedSource = Files.readAllBytes(store.source(vid, 1).orElseThrow());
        String sharedDefinition = store.definition(vid, 1).orElseThrow();

        // when: two applicants open the same link and build, and the first one fixes an answer
        mockMvc.perform(get("/api/forms/22382"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.pdfFirst").value(false))
                .andExpect(jsonPath("$.items[0].id").value("name"));
        MvcResult first = generate(1, null, "김배우", "f");
        MvcResult second = generate(1, null, "이배우", "m");
        String firstId = first.getResponse().getHeader(FormRequests.DOCUMENT_ID);
        String secondId = second.getResponse().getHeader(FormRequests.DOCUMENT_ID);
        MvcResult fixed = generate(1, firstId, "김배우수정", "f");

        // then
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(fixed.getResponse().getHeader(FormRequests.DOCUMENT_ID)).isEqualTo(firstId);
        Map<CellAddress, String> firstFile = cells(completed(firstId));
        Map<CellAddress, String> secondFile = cells(completed(secondId));
        assertThat(firstFile.get(NAME)).isEqualTo("김배우수정");
        assertThat(firstFile.get(GENDER)).isEqualTo("남(  )  여( V )");
        assertThat(secondFile.get(NAME)).isEqualTo("이배우");
        assertThat(secondFile.get(GENDER)).isEqualTo("남( V )  여(  )");
        assertThat(secondFile.get(GUARDIAN)).isEqualTo("보호자 / 010-9999-8888");
        assertThat(Files.readAllBytes(store.source(vid, 1).orElseThrow())).isEqualTo(sharedSource);
        assertThat(store.definition(vid, 1).orElseThrow()).isEqualTo(sharedDefinition);
    }

    @Test
    @DisplayName("수정본을 공개해도 열어 둔 이전 버전 화면은 그대로 완성되고, 공개를 끝내면 막힌다")
    void noticeLink_KeepsOpenedVersionThenStops_WhenFormIsRepublishedAndClosed() throws Exception {
        // given: version 1 is published, then the operator edits and publishes version 2
        register(new Vid("30001"));
        testBuild("30001");
        mockMvc.perform(admin(post("/api/admin/forms/30001/publish"))).andExpect(status().isOk());
        String edited = FormDefinitionParserTest.sampleDefinition().replace("배우 지원서\"", "배우 지원서 (수정)\"");
        mockMvc.perform(admin(put("/api/admin/forms/30001/definition")).contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(jsonPath("$.editingVersion").value(2));
        testBuild("30001");
        mockMvc.perform(admin(post("/api/admin/forms/30001/publish"))).andExpect(status().isOk());

        // when / then
        mockMvc.perform(get("/api/forms/30001")).andExpect(jsonPath("$.version").value(2));
        perform("30001", 1, null, "옛화면배우", "m").andExpect(status().isOk());
        perform("30001", 7, null, "없는버전", "m")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FORM_CHANGED"));
        mockMvc.perform(admin(post("/api/admin/forms/30001/close"))).andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(get("/api/forms/30001")).andExpect(status().isGone());
        perform("30001", 2, null, "마감후배우", "m").andExpect(status().isGone());
    }

    @Test
    @DisplayName("배우가 고친 파일 이름으로 완성되고, 안 고치면 운영자 템플릿 이름을 쓴다")
    void generate_UsesApplicantsFileName_WhenOneIsChosen() throws Exception {
        // given
        register(new Vid("30002"));
        testBuild("30002");
        mockMvc.perform(admin(post("/api/admin/forms/30002/publish"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/forms/30002"))
                .andExpect(jsonPath("$.fileName").value("{name}_{role}_지원서"))
                .andExpect(jsonPath("$.sourceName").value("sample-notice.hwp"));

        // when / then
        mockMvc.perform(multipart("/api/forms/{vid}/generate", "30002")
                        .file(request(1, null, "김배우", "f", "김배우_최종본.hwp")).file(photo()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(encoded("김배우_최종본.hwp"))));
        mockMvc.perform(multipart("/api/forms/{vid}/generate", "30002")
                        .file(request(1, null, "이배우", "m", "")).file(photo()))
                .andExpect(header().string("Content-Disposition", containsString(encoded("이배우_곰역_지원서.hwp"))));
        mockMvc.perform(multipart("/api/forms/{vid}/generate", "30002")
                        .file(request(1, null, "박배우", "m", "a/b.hwp")).file(photo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_NAME"));
    }

    private static String encoded(String fileName) {
        return java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void register(Vid vid) throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "sample-notice.hwp", "application/x-hwp", resource("/forms/sample-notice.hwp"));
        mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/source", vid.value()).file(document)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cells[?(@.address == '0.0.4')].text").value("사진"));
        mockMvc.perform(admin(put("/api/admin/forms/{vid}/definition", vid.value()))
                        .contentType(MediaType.APPLICATION_JSON).content(FormDefinitionParserTest.sampleDefinition()))
                .andExpect(jsonPath("$.problems").isEmpty())
                .andExpect(jsonPath("$.tested").value(false));
    }

    private void testBuild() throws Exception {
        testBuild("22382");
    }

    private void testBuild(String vid) throws Exception {
        mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/test", vid)
                        .file(request(0, null, "운영자테스트", "m")).file(photo())))
                .andExpect(status().isOk());
    }

    private MvcResult generate(int version, String documentId, String name, String gender) throws Exception {
        return perform("22382", version, documentId, name, gender).andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            String vid, int version, String documentId, String name, String gender) throws Exception {
        return mockMvc.perform(multipart("/api/forms/{vid}/generate", vid)
                .file(request(version, documentId, name, gender)).file(photo()));
    }

    private static MockMultipartFile request(int version, String documentId, String name, String gender) {
        return request(version, documentId, name, gender, "");
    }

    private static MockMultipartFile request(
            int version, String documentId, String name, String gender, String fileName) {
        String json = """
                {"version": %d, "documentId": %s, "fileName": "%s", "answers": {
                  "name": ["%s"], "birth": ["1996.03.01"], "phone": ["01012345678"], "gender": ["%s"],
                  "role": ["bear"], "guardianName": ["보호자"], "guardianPhone": ["010-9999-8888"]}}
                """.formatted(version, documentId == null ? "null" : "\"" + UUID.fromString(documentId) + "\"",
                fileName, name, gender);
        return new MockMultipartFile("request", "request.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B admin(B request) {
        return request.header("Authorization", "Bearer " + TOKEN);
    }

    private byte[] completed(String documentId) throws Exception {
        return mockMvc.perform(get("/api/documents/{id}/completed", documentId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static Map<CellAddress, String> cells(byte[] hwp) throws Exception {
        Path file = Files.createTempFile("notice-form-", ".hwp");
        try {
            Files.write(file, hwp);
            return HwpDocument.open(file).cells().stream()
                    .collect(Collectors.toMap(CellSnapshot::address, CellSnapshot::text));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** A drawn placeholder portrait: no real person's photo goes into the repository. */
    private static MockMultipartFile photo() throws IOException {
        BufferedImage image = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(200, 220, 235));
        graphics.fillRect(0, 0, 300, 400);
        graphics.setColor(new Color(240, 200, 170));
        graphics.fillOval(80, 60, 140, 180);
        graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return new MockMultipartFile("photo-photo", "portrait.png", "image/png", png.toByteArray());
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = NoticeFormHttpAcceptanceTest.class.getResourceAsStream(path)) {
            return input.readAllBytes();
        }
    }
}
