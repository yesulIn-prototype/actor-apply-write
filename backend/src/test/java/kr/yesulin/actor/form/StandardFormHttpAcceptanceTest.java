package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellSnapshot;
import kr.yesulin.actor.document.HwpDocument;
import kr.yesulin.actor.document.PdfConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The 예술in standard form (backend/src/main/resources/standard) built through the operator API. */
@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-standard-test/work",
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-actor-standard-test/forms-${random.uuid}",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-standard-test/completed-count.txt",
        "yesulin.admin-token=" + NoticeFormHttpAcceptanceTest.TOKEN})
@AutoConfigureMockMvc
class StandardFormHttpAcceptanceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PdfConverter rhwp;

    @Test
    @DisplayName("경력을 양식 10줄보다 많이 쓰고 항목을 더하면 표에 줄이 생기고 아래 칸이 밀려 채워진다")
    void testBuild_AddsTableRows_WhenCareerAndOwnItemsRunPastTheForm() throws Exception {
        // given
        register("40001");

        // when
        byte[] built = testBuild("40001", null, 12, true).getResponse().getContentAsByteArray();

        // then
        Map<CellAddress, String> cells = cells(built);
        assertThat(cells.get(new CellAddress(0, 0, 1))).isEqualTo("김배우");
        assertThat(cells.get(new CellAddress(0, 2, 1))).isEqualTo("남(  )  여( V )");
        assertThat(cells.get(new CellAddress(1, 3, 0))).isEqualTo("영상 링크");
        assertThat(cells.get(new CellAddress(1, 4, 1))).isEqualTo("2024 신인연기상");
        assertThat(cells.get(new CellAddress(1, 5, 0))).isEqualTo("자기소개·지원동기");
        assertThat(cells.get(new CellAddress(1, 5, 1))).isEqualTo("무대가 좋습니다");
        assertThat(cells.get(new CellAddress(2, 1, 0))).isEqualTo("작품 1");
        assertThat(cells.get(new CellAddress(2, 12, 0))).isEqualTo("작품 12");
        assertThat(cells.get(new CellAddress(2, 12, 4))).isEqualTo("극단 12");
        assertThat(cells.keySet().stream().filter(cell -> cell.tableIndex() == 2).mapToInt(CellAddress::rowIndex).max())
                .hasValue(12);
        assertThat(cells.keySet().stream().filter(cell -> cell.tableIndex() == 3)).hasSize(2);
    }

    @Test
    @DisplayName("미리보기에서 누를 칸은 늘어난 줄을 따라가고, 줄을 줄여 다시 만들면 되돌아온다")
    void preview_FollowsAddedRows_WhenTheSameApplicantRebuildsWithMoreOrFewerRows() throws Exception {
        // given: an applicant built with 12 career rows and 2 items of their own
        register("40002");
        String job = testBuild("40002", null, 12, true).getResponse().getHeader(FormRequests.DOCUMENT_ID);
        Map<String, List<JsonNode>> grown = hotspots(job);

        // when: they rebuild the same job with 3 career rows and no items of their own
        testBuild("40002", job, 3, false);
        Map<String, List<JsonNode>> shrunk = hotspots(job);

        // then
        assertThat(grown.get("career")).hasSize(12 * 5);
        assertThat(grown.get("more")).hasSize(2 * 2);
        assertThat(top(grown.get("intro"))).isGreaterThan(grown.get("more").stream().mapToDouble(StandardFormHttpAcceptanceTest::top).max().orElseThrow());
        assertThat(shrunk.get("career")).hasSize(10 * 5);
        assertThat(shrunk).doesNotContainKey("more");
        assertThat(top(shrunk.get("intro"))).isLessThan(top(grown.get("intro")));
    }

    private void register(String vid) throws Exception {
        assumeTrue(rhwp.available(), "rhwp not installed — run tools/install-rhwp.sh");
        MockMultipartFile source = new MockMultipartFile(
                "document", "standard-v1.hwp", "application/x-hwp", resource("/standard/standard-v1.hwp"));
        mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/source", vid).file(source)))
                .andExpect(status().isOk());
        mockMvc.perform(admin(put("/api/admin/forms/{vid}/definition", vid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new String(resource("/standard/standard-v1.definition.json"), StandardCharsets.UTF_8)))
                .andExpect(jsonPath("$.problems").isEmpty());
    }

    private MvcResult testBuild(String vid, String documentId, int careerRows, boolean ownItems) throws Exception {
        return mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/test", vid)
                        .file(request(documentId, careerRows, ownItems)).file(photo())))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** The preview's tap areas by the answer they open. */
    private Map<String, List<JsonNode>> hotspots(String documentId) throws Exception {
        String body = mockMvc.perform(get("/api/documents/{id}/preview", documentId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<JsonNode> all = new ArrayList<>();
        JSON.readTree(body).path("hotspots").forEach(all::add);
        return all.stream().collect(Collectors.groupingBy(hotspot -> hotspot.path("fieldId").asString()));
    }

    /** Page and height on the page together: a later page is always lower. */
    private static double top(JsonNode hotspot) {
        return hotspot.path("page").asInt() * 100_000 + hotspot.path("y").asDouble();
    }

    private static double top(List<JsonNode> hotspots) {
        return top(hotspots.getFirst());
    }

    private static MockMultipartFile request(String documentId, int careerRows, boolean ownItems) {
        List<String> career = new ArrayList<>();
        for (int entry = 1; entry <= careerRows; entry++) {
            career.addAll(List.of("작품 " + entry, "역할 " + entry, "2025." + entry, "극장 " + entry, "극단 " + entry));
        }
        Map<String, List<String>> answers = new java.util.HashMap<>(Map.of(
                "name", List.of("김배우"), "birth", List.of("1998.03.01"), "phone", List.of("01012345678"),
                "gender", List.of("f"), "intro", List.of("무대가 좋습니다"), "career", career));
        if (ownItems) {
            answers.put("more", List.of("영상 링크", "youtu.be/example", "수상", "2024 신인연기상"));
        }
        Map<String, Object> request = new java.util.HashMap<>(Map.of("version", 0, "fileName", "", "answers", answers));
        if (documentId != null) {
            request.put("documentId", documentId);
        }
        return new MockMultipartFile("request", "request.json", "application/json", JSON.writeValueAsBytes(request));
    }

    private static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B admin(
            B request) {
        return request.header("Authorization", "Bearer " + NoticeFormHttpAcceptanceTest.TOKEN);
    }

    private static Map<CellAddress, String> cells(byte[] hwp) throws Exception {
        Path file = Files.createTempFile("standard-form-", ".hwp");
        try {
            Files.write(file, hwp);
            return HwpDocument.open(file).cells().stream()
                    .collect(Collectors.toMap(CellSnapshot::address, CellSnapshot::text));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** A plain drawn square: no real person's photo goes into the repository. */
    private static MockMultipartFile photo() throws IOException {
        BufferedImage image = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return new MockMultipartFile("photo-photoMain", "portrait.png", "image/png", png.toByteArray());
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = StandardFormHttpAcceptanceTest.class.getResourceAsStream(path)) {
            return input.readAllBytes();
        }
    }
}
