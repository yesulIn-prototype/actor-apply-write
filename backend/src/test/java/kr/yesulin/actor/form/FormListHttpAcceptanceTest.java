package kr.yesulin.actor.form;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-form-list-${random.uuid}",
        "yesulin.admin-token=list-test-token"})
@AutoConfigureMockMvc
class FormListHttpAcceptanceTest {
    @Autowired private MockMvc mvc;
    @Autowired private FormStore store;

    @BeforeEach
    void notices() throws IOException {
        for (int index = 0; index < 25; index++) {
            Vid vid = new Vid(Integer.toString(10000 + index));
            int version = store.draft(vid);
            String title = index == 24 ? "극단웃어 단원 지원서" : "Synthetic 공고 " + index;
            store.saveDefinition(vid, version, """
                    {"title":"%s","items":[{"id":"name","label":"이름","type":"text"}],
                    "outputs":[{"cell":"0.0.1","text":"{name}"}]}
                    """.formatted(title));
        }
    }

    @Test
    void list_ReturnsTwentyNewestNotices_WhenFirstPageIsRequested() throws Exception {
        // given / when / then
        mvc.perform(get("/api/admin/forms").header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].vid").value("10024"))
                .andExpect(jsonPath("$.items[19].vid").value("10005"))
                .andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20)).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void list_ReturnsRemainingNotices_WhenSecondPageIsRequested() throws Exception {
        mvc.perform(get("/api/admin/forms").param("page", "2").header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].vid").value("10004"));
    }

    @Test
    void list_FiltersBeforePaging_WhenVidOrTitleIsSearched() throws Exception {
        mvc.perform(get("/api/admin/forms").param("query", "  웃어  ").param("page", "999")
                        .header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items[0].vid").value("10024"));
        mvc.perform(get("/api/admin/forms").param("query", "1000").header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(10));
        mvc.perform(get("/api/admin/forms").param("query", "sYnThEtIc").header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(24));
    }

    @Test
    void list_ReturnsAnEmptyFirstPage_WhenNothingMatches() throws Exception {
        mvc.perform(get("/api/admin/forms").param("query", "없는공고").header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void list_RejectsInvalidPageOrMissingToken_WhenRequestIsInvalid() throws Exception {
        mvc.perform(get("/api/admin/forms").param("page", "0").header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/forms").param("page", "-1").header("Authorization", "Bearer list-test-token"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/forms").param("query", "웃어")).andExpect(status().isUnauthorized());
    }
}
