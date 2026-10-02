package kr.yesulin.actor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** A job id is enough to download an applicant's finished file, so no log line may carry one. */
@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-log-test/work",
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-actor-log-test/forms",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-log-test/completed-count.txt"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class JobIdLoggingTest {
    private static final String JOB = "7907f91f-de04-418f-a284-75836cbebce5";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("요청 로그의 주소에서 작업 ID를 가린다")
    void requestLog_MasksJobId_WhenPathNamesAJob(CapturedOutput output) throws Exception {
        // when
        mockMvc.perform(get("/api/documents/{id}/completed", JOB)).andExpect(status().isNotFound());

        // then
        assertThat(output).contains("GET /api/documents/<job>/completed -> 404").doesNotContain(JOB);
    }

    @Test
    @DisplayName("예외 내용(작업공간 경로 등)에 든 작업 ID도 가린다")
    void stackTrace_MasksJobId_WhenAnErrorQuotesAWorkspacePath(CapturedOutput output) {
        // when
        LoggerFactory.getLogger(JobIdLoggingTest.class)
                .error("unexpected failure", new IOException("/tmp/yesulin-actor/" + JOB + "/completed.hwp"));

        // then
        assertThat(output).contains("/tmp/yesulin-actor/<job>/completed.hwp").doesNotContain(JOB);
    }
}
