package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
import tools.jackson.databind.json.JsonMapper;

/** The 예술in standard form (backend/src/main/resources/standard) built through the operator API. */
@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-standard-test/work",
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-actor-standard-test/forms-${random.uuid}",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-standard-test/completed-count.txt",
        "yesulin.admin-token=" + NoticeFormHttpAcceptanceTest.TOKEN})
@AutoConfigureMockMvc
class StandardFormHttpAcceptanceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PdfConverter rhwp;

    @Test
    @DisplayName("경력을 양식 10줄보다 많이 쓰고 항목을 더하면 표에 줄이 생기고 아래 칸이 밀려 채워진다")
    void testBuild_AddsTableRows_WhenCareerAndOwnItemsRunPastTheForm() throws Exception {
        // given
        assumeTrue(rhwp.available(), "rhwp not installed — run tools/install-rhwp.sh");
        MockMultipartFile source = new MockMultipartFile(
                "document", "standard-v1.hwp", "application/x-hwp", resource("/standard/standard-v1.hwp"));
        mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/source", "40001").file(source)))
                .andExpect(status().isOk());
        mockMvc.perform(admin(put("/api/admin/forms/{vid}/definition", "40001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new String(resource("/standard/standard-v1.definition.json"), StandardCharsets.UTF_8)))
                .andExpect(jsonPath("$.problems").isEmpty());

        // when
        byte[] built = mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/test", "40001")
                        .file(request()).file(photo())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

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

    private static MockMultipartFile request() {
        List<String> career = new ArrayList<>();
        for (int entry = 1; entry <= 12; entry++) {
            career.addAll(List.of("작품 " + entry, "역할 " + entry, "2025." + entry, "극장 " + entry, "극단 " + entry));
        }
        Map<String, Object> request = Map.of("version", 0, "fileName", "", "answers", Map.of(
                "name", List.of("김배우"), "birth", List.of("1998.03.01"), "phone", List.of("01012345678"),
                "gender", List.of("f"), "intro", List.of("무대가 좋습니다"), "career", career,
                "more", List.of("영상 링크", "youtu.be/example", "수상", "2024 신인연기상")));
        byte[] json = JsonMapper.builder().build().writeValueAsBytes(request);
        return new MockMultipartFile("request", "request.json", "application/json", json);
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
