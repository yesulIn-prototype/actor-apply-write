package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-http-test",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-http-test-stats/completed-count.txt"})
@AutoConfigureMockMvc
class DocumentHttpAcceptanceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("실제 HWP를 분석하고 텍스트와 사진이 반영된 HWP를 내려받는다")
    void generate_ReturnsReopenableHwp_WhenMultipartRequestIsConfirmed() throws Exception {
        // given
        MockMultipartFile document = new MockMultipartFile(
                "document", "공부의신_오디션지원서.hwp", "application/x-hwp",
                fixtureBytes("application.hwp"));
        MvcResult analysisResult = mockMvc.perform(multipart("/api/documents/analyze").file(document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.label == '이름')]").exists())
                .andExpect(jsonPath("$.fields[?(@.label == '사진1')]").exists())
                .andReturn();
        String documentId = jsonMapper.readTree(analysisResult.getResponse().getContentAsByteArray())
                .required("documentId")
                .asText();
        String requestJson = """
                {
                  "textValues": [{
                    "fieldId": "text-0-0-1",
                    "address": {"tableIndex": 0, "rowIndex": 0, "cellIndex": 1},
                    "value": "HTTP테스트배우"
                  }],
                  "photos": [{
                    "fieldId": "photo-1-0-0",
                    "address": {"tableIndex": 1, "rowIndex": 0, "cellIndex": 0},
                    "fileKey": "portrait"
                  }]
                }
                """;
        MockMultipartFile request = new MockMultipartFile(
                "request", "request.json", "application/json",
                requestJson.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile portrait = new MockMultipartFile(
                "portrait", "portrait.jpg", "image/jpeg", fixtureBytes("portrait.jpg"));

        // when
        MvcResult generated = mockMvc.perform(multipart("/api/documents/{documentId}/generate", documentId)
                        .file(request)
                        .file(portrait))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-hwp"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        // then
        Path output = temporaryDirectory.resolve("http-completed.hwp");
        Files.write(output, generated.getResponse().getContentAsByteArray());
        HwpDocument reopened = HwpDocument.open(output);
        assertThat(reopened.cells())
                .filteredOn(cell -> cell.address().equals(new CellAddress(0, 0, 1)))
                .extracting(CellSnapshot::text)
                .containsExactly("HTTP테스트배우");
        assertThat(reopened.embeddedImageCount()).isGreaterThan(0);
        mockMvc.perform(get("/api/documents/{documentId}/completed", documentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-hwp"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(generated.getResponse().getContentAsByteArray()));
        mockMvc.perform(get("/api/documents/{documentId}/completed.pdf", documentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")));
        mockMvc.perform(get("/api/documents/{documentId}/preview", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages.length()").value(2))
                .andExpect(jsonPath("$.hotspots[?(@.fieldId == 'text-0-0-1' && @.page == 1)]").exists())
                .andExpect(jsonPath("$.hotspots[?(@.fieldId == 'photo-1-0-0' && @.page == 2)]").exists());
        mockMvc.perform(get("/api/documents/{documentId}/preview/{page}", documentId, 1))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("<svg")));
        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").isNumber());
    }

    @Test
    @DisplayName("완성본을 만들기 전에는 GET 다운로드가 404를 반환한다")
    void completed_ReturnsNotFound_WhenNothingWasGenerated() throws Exception {
        // given
        MockMultipartFile document = new MockMultipartFile(
                "document", "공부의신_오디션지원서.hwp", "application/x-hwp",
                fixtureBytes("application.hwp"));
        MvcResult analysisResult = mockMvc.perform(multipart("/api/documents/analyze").file(document))
                .andExpect(status().isOk())
                .andReturn();
        String documentId = jsonMapper.readTree(analysisResult.getResponse().getContentAsByteArray())
                .required("documentId")
                .asText();

        // when & then
        mockMvc.perform(get("/api/documents/{documentId}/completed", documentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("확장자만 HWP인 파일은 구체적인 400 오류를 반환한다")
    void analyze_ReturnsBadRequest_WhenHwpSignatureIsInvalid() throws Exception {
        // given
        MockMultipartFile invalid = new MockMultipartFile(
                "document", "가짜지원서.hwp", "application/x-hwp",
                "not-an-hwp".getBytes(StandardCharsets.UTF_8));

        // when & then
        mockMvc.perform(multipart("/api/documents/analyze").file(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPLOAD"))
                .andExpect(jsonPath("$.message").value("정상적인 한글 문서가 아닙니다."));
    }

    @Test
    @DisplayName("사용자가 정한 이름으로 한글과 PDF 완성본을 내려받는다")
    void generate_UsesChosenFileName_ForBothDownloadFormats() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "공부의신_오디션지원서.hwp", "application/x-hwp", fixtureBytes("application.hwp"));
        MvcResult analysis = mockMvc.perform(multipart("/api/documents/analyze").file(document))
                .andExpect(status().isOk()).andReturn();
        String id = jsonMapper.readTree(analysis.getResponse().getContentAsByteArray())
                .required("documentId").asText();
        MockMultipartFile request = new MockMultipartFile("request", "request.json", "application/json", """
                {"fileName":"김배우_극단지원","textValues":[{"fieldId":"text-0-0-1",
                 "address":{"tableIndex":0,"rowIndex":0,"cellIndex":1},"value":"김배우"}],"photos":[]}
                """.getBytes(StandardCharsets.UTF_8));

        MvcResult generated = mockMvc.perform(multipart("/api/documents/{documentId}/generate", id).file(request))
                .andExpect(status().isOk()).andReturn();

        assertThat(org.springframework.http.ContentDisposition.parse(
                generated.getResponse().getHeader("Content-Disposition")).getFilename())
                .isEqualTo("김배우_극단지원.hwp");
        MvcResult downloaded = mockMvc.perform(get("/api/documents/{documentId}/completed", id))
                .andExpect(status().isOk()).andReturn();
        assertThat(org.springframework.http.ContentDisposition.parse(
                downloaded.getResponse().getHeader("Content-Disposition")).getFilename())
                .isEqualTo("김배우_극단지원.hwp");
        MvcResult pdf = mockMvc.perform(get("/api/documents/{documentId}/completed.pdf", id))
                .andExpect(status().isOk()).andReturn();
        assertThat(org.springframework.http.ContentDisposition.parse(
                pdf.getResponse().getHeader("Content-Disposition")).getFilename())
                .isEqualTo("김배우_극단지원.pdf");
    }

    @Test
    @DisplayName("경로나 제어 문자가 포함된 파일명은 거부한다")
    void generate_RejectsUnsafeFileName() throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "공부의신_오디션지원서.hwp", "application/x-hwp", fixtureBytes("application.hwp"));
        MvcResult analysis = mockMvc.perform(multipart("/api/documents/analyze").file(document))
                .andExpect(status().isOk()).andReturn();
        String id = jsonMapper.readTree(analysis.getResponse().getContentAsByteArray())
                .required("documentId").asText();
        MockMultipartFile request = new MockMultipartFile("request", "request.json", "application/json", """
                {"fileName":"../another-file","textValues":[{"fieldId":"text-0-0-1",
                 "address":{"tableIndex":0,"rowIndex":0,"cellIndex":1},"value":"김배우"}],"photos":[]}
                """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents/{documentId}/generate", id).file(request))
                .andExpect(status().isBadRequest());
    }

    private static byte[] fixtureBytes(String name) throws Exception {
        try (var input = DocumentHttpAcceptanceTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return input.readAllBytes();
        }
    }
}
