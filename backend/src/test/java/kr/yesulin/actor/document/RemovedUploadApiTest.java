package kr.yesulin.actor.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-removed-api-test/work",
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-actor-removed-api-test/forms",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-removed-api-test/count.txt"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RemovedUploadApiTest {
    private final MockMvc http;
    private final DocumentService documents;

    @TempDir
    Path work;

    RemovedUploadApiTest(MockMvc http, DocumentService documents) {
        this.http = http;
        this.documents = documents;
    }

    @Test
    void analyze_ReturnsMethodNotAllowed_WhenDirectUploadIsRequested() throws Exception {
        // given: a valid synthetic HWP, so upload validation cannot hide a surviving endpoint
        byte[] source;
        try (var input = getClass().getResourceAsStream("/forms/sample-notice.hwp")) {
            source = input.readAllBytes();
        }
        MockMultipartFile upload = new MockMultipartFile("document", "sample.hwp", "application/x-hwp", source);

        // when / then
        http.perform(multipart("/api/documents/analyze").file(upload)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void stats_ReturnsNotFound_WhenLegacyLandingCountIsRequested() throws Exception {
        // when / then
        http.perform(get("/api/stats")).andExpect(status().isNotFound());
    }

    @Test
    void generate_ReturnsNotFound_WhenLegacyBuildTargetsAnExistingNoticeJob() throws Exception {
        // given: a real notice job, so an unknown job cannot hide a surviving endpoint
        Path source = work.resolve("sample.hwp");
        try (var input = getClass().getResourceAsStream("/forms/sample-notice.hwp")) {
            Files.write(source, input.readAllBytes());
        }
        UUID job = documents.startJob("sample.hwp", source, "form:12345:v1");
        MockMultipartFile request = new MockMultipartFile("request", "request.json", "application/json",
                """
                {"textValues":[{"fieldId":"text-0-0-1",
                  "address":{"tableIndex":0,"rowIndex":0,"cellIndex":1},"value":"예시 입력"}],"photos":[]}
                """.getBytes(StandardCharsets.UTF_8));

        // when / then
        http.perform(multipart("/api/documents/{id}/generate", job).file(request))
                .andExpect(status().isNotFound());
    }
}
