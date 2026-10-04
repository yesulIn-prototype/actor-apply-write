package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import kr.yesulin.actor.document.PdfConverter;
import kr.yesulin.actor.document.HwpDocumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-management-${random.uuid}",
        "yesulin.workspace=${java.io.tmpdir}/yesulin-management-work-${random.uuid}",
        "yesulin.admin-token=management-test-token"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FormManagementHttpAcceptanceTest {
    @Autowired private MockMvc mvc;
    @Autowired private FormStore store;
    @MockitoBean private PdfConverter renderer;
    private static final String AUTH = "Bearer management-test-token";
    private static final String VISITOR = "00000000-0000-4000-8000-000000000001";
    private static final String DEFINITION = """
            {"title":"합성 지원서","items":[{"id":"name","label":"성명","type":"text","required":true}],
            "outputs":[{"cell":"0.0.1","text":"{name}"}]}
            """;

    @BeforeEach
    void source() throws Exception {
        doAnswer(call -> { Files.writeString(call.getArgument(1, Path.class), "%PDF-1.4 synthetic"); return null; })
                .when(renderer).convert(any(Path.class), any(Path.class));
    }

    private void register(String number) throws IOException {
        Vid vid = new Vid(number);
        int version = store.draft(vid);
        try (var input = getClass().getResourceAsStream("/forms/sample-notice.hwp")) {
            store.saveSource(vid, version, "sample.hwp", input.readAllBytes());
        }
        store.saveDefinition(vid, version, DEFINITION);
        store.publish(vid, version);
    }

    @Test
    void delete_HidesAndBlocksNewApplicationsThenRestores_WhenNoticeIsDeleted() throws Exception {
        // given
        register("70001");
        byte[] original = Files.readAllBytes(store.source(new Vid("70001"), 1).orElseThrow());
        // when
        mvc.perform(delete("/api/admin/forms/70001").header("Authorization", AUTH)).andExpect(status().isNoContent());
        // then
        mvc.perform(get("/api/admin/forms").param("query", "70001").header("Authorization", AUTH))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/forms").param("deleted", "true").param("query", "70001").header("Authorization", AUTH))
                .andExpect(jsonPath("$.items[0].status").value("DELETED"));
        mvc.perform(get("/api/forms/70001")).andExpect(status().isNotFound());
        generate("70001", null, null, VISITOR, "/api/forms/70001/generate").andExpect(status().isNotFound());
        assertThat(Files.readAllBytes(store.source(new Vid("70001"), 1).orElseThrow())).isEqualTo(original);
        mvc.perform(post("/api/admin/forms/70001/restore").header("Authorization", AUTH)).andExpect(status().isOk());
        mvc.perform(get("/api/forms/70001")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/forms/70001")).andExpect(status().isUnauthorized());
    }

    @Test
    void usage_CountsBrowsersAndFirstHwpPdfAcrossNotices_ExcludingRebuildsAndTests() throws Exception {
        // given
        register("70002"); register("70003");
        MvcResult first = generate("70002", null, null, VISITOR, "/api/forms/70002/generate")
                .andExpect(status().isOk()).andReturn();
        String job = first.getResponse().getHeader("X-Document-Id");
        String token = first.getResponse().getHeader("X-Document-Edit-Token");
        // when
        mvc.perform(get("/api/documents/{id}/completed.pdf", job)).andExpect(status().isOk());
        mvc.perform(get("/api/documents/{id}/completed.pdf", job)).andExpect(status().isOk());
        generate("70002", job, token, VISITOR, "/api/forms/70002/generate").andExpect(status().isOk());
        mvc.perform(get("/api/documents/{id}/completed.pdf", job)).andExpect(status().isOk());
        generate("70003", null, null, VISITOR, "/api/forms/70003/generate").andExpect(status().isOk());
        MvcResult operatorTest = generate("70002", null, null, VISITOR, "/api/admin/forms/70002/test")
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/documents/{id}/completed.pdf", operatorTest.getResponse().getHeader("X-Document-Id")))
                .andExpect(status().isOk());
        // then
        mvc.perform(get("/api/admin/forms").param("query", "70002").header("Authorization", AUTH))
                .andExpect(jsonPath("$.items[0].usage.visitors").value(1))
                .andExpect(jsonPath("$.items[0].usage.hwp").value(1)).andExpect(jsonPath("$.items[0].usage.pdf").value(1));
        mvc.perform(get("/api/admin/usage").header("Authorization", AUTH))
                .andExpect(jsonPath("$.counts.visitors").value(1))
                .andExpect(jsonPath("$.counts.hwp").value(2)).andExpect(jsonPath("$.counts.pdf").value(1));
    }

    @Test
    void usage_DoesNotCountFailedPdf_WhenRendererFails() throws Exception {
        // given
        register("70004");
        MvcResult generated = generate("70004", null, null, VISITOR, "/api/forms/70004/generate").andReturn();
        doThrow(new HwpDocumentException("synthetic renderer failure")).when(renderer).convert(any(Path.class), any(Path.class));
        // when
        mvc.perform(get("/api/documents/{id}/completed.pdf", generated.getResponse().getHeader("X-Document-Id")))
                .andExpect(status().is(422));
        // then
        mvc.perform(get("/api/admin/forms").param("query", "70004").header("Authorization", AUTH))
                .andExpect(jsonPath("$.items[0].usage.pdf").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions generate(String vid, String job, String token,
            String visitor, String url) throws Exception {
        var request = Map.of("version", 1, "answers", Map.of("name", List.of("합성인물")),
                "documentId", job == null ? "" : job);
        String json = JsonMapper.builder().build().writeValueAsString(request).replace("\"documentId\":\"\"", "\"documentId\":null");
        var call = multipart(url).file(new MockMultipartFile("request", "request.json", "application/json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("X-Usage-Visitor", visitor).header("Authorization", AUTH);
        if (token != null) call.header("X-Document-Edit-Token", token);
        return mvc.perform(call);
    }
}
