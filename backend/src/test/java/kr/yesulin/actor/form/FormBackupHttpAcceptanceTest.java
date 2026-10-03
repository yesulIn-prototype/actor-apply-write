package kr.yesulin.actor.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

/** The operator's backup of every notice form, and putting it back after the forms folder was lost. */
@SpringBootTest(properties = {
        "yesulin.workspace=${java.io.tmpdir}/yesulin-actor-backup-test/work",
        "yesulin.forms-dir=${java.io.tmpdir}/yesulin-actor-backup-test/forms-${random.uuid}",
        "yesulin.stats-file=${java.io.tmpdir}/yesulin-actor-backup-test/completed-count.txt",
        "yesulin.admin-token=" + NoticeFormHttpAcceptanceTest.TOKEN})
@AutoConfigureMockMvc
class FormBackupHttpAcceptanceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FormStore store;

    @Autowired
    private kr.yesulin.actor.document.PdfConverter rhwp;

    @Test
    @DisplayName("백업을 받아 두면 공고 폴더를 잃어도 올려서 같은 링크·같은 버전으로 되살린다")
    void restore_BringsBackLostNotices_WithTheirPublishedVersions() throws Exception {
        // given: 50001 is published, and its folder is backed up and then lost
        publishSample("50001");
        mockMvc.perform(admin(get("/api/admin/forms/50001/layout"))).andExpect(status().isOk());
        byte[] backup = download();
        byte[] source = Files.readAllBytes(store.source(new Vid("50001"), 1).orElseThrow());
        deleteTree(store.root().resolve("50001"));
        mockMvc.perform(get("/api/forms/50001")).andExpect(status().isNotFound());

        // when
        restore(backup).andExpect(status().isOk())
                .andExpect(jsonPath("$.restored", hasItem("50001")));

        // then
        assertThat(entries(backup)).contains("backup.json", "50001/state.json", "50001/v1/source.hwp",
                "50001/v1/definition.json", "50001/v1/version.json").noneMatch(name -> name.contains("render"));
        assertThat(Files.readAllBytes(store.source(new Vid("50001"), 1).orElseThrow())).isEqualTo(source);
        mockMvc.perform(get("/api/forms/50001")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(multipart("/api/forms/{vid}/generate", "50001").file(answers(1)).file(photo()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("표준 지원서 공고는 운영자가 쓴 공고 설정도 함께 백업한다")
    void backup_KeepsTheSettingsOfAStandardFormNotice() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(rhwp.available(), "rhwp not installed — run tools/install-rhwp.sh");
        mockMvc.perform(admin(put("/api/admin/forms/50004/definition"))
                        .contentType(MediaType.APPLICATION_JSON).content(StandardNoticeHttpAcceptanceTest.SPEC))
                .andExpect(jsonPath("$.problems").isEmpty());

        assertThat(entries(download())).contains("50004/v1/spec.json", "50004/v1/source.hwp", "50004/v1/definition.json");
    }

    @Test
    @DisplayName("이미 있는 공고는 백업으로 덮어쓰지 않는다")
    void restore_LeavesExistingNoticesAlone_WhenTheBackupAlsoHasThem() throws Exception {
        // given: a backup of 50002, after which the operator edited it again
        publishSample("50002");
        byte[] backup = download();
        String edited = FormDefinitionParserTest.sampleDefinition().replace("배우 지원서\"", "배우 지원서 (수정)\"");
        mockMvc.perform(admin(put("/api/admin/forms/50002/definition")).contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(jsonPath("$.editingVersion").value(2));

        // when / then
        restore(backup).andExpect(status().isOk()).andExpect(jsonPath("$.skipped", hasItem("50002")));
        assertThat(store.definition(new Vid("50002"), 2)).contains(edited);
    }

    @Test
    @DisplayName("우리 백업이 아니거나 폴더 밖을 가리키는 파일은 거절하고 아무것도 들이지 않는다")
    void restore_RefusesForeignOrEscapingFiles() throws Exception {
        restore(zip("notes.txt", "hello")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BACKUP"));
        restore(zip("backup.json", "{\"format\":1,\"createdAt\":\"\",\"forms\":[]}", "../50003/state.json", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BACKUP"));
        restore(zip("backup.json", "{\"format\":1,\"createdAt\":\"\",\"forms\":[]}", "50003/state.json", "not json"))
                .andExpect(status().isBadRequest());

        assertThat(store.all()).noneMatch(vid -> vid.value().equals("50003"));
        try (var left = Files.list(store.root())) {
            assertThat(left).noneMatch(path -> path.getFileName().toString().startsWith(".restore-"));
        }
        mockMvc.perform(get("/api/admin/backup")).andExpect(status().isUnauthorized());
    }

    private void publishSample(String vid) throws Exception {
        MockMultipartFile document = new MockMultipartFile(
                "document", "sample-notice.hwp", "application/x-hwp", resource("/forms/sample-notice.hwp"));
        mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/source", vid).file(document))).andExpect(status().isOk());
        mockMvc.perform(admin(put("/api/admin/forms/{vid}/definition", vid))
                        .contentType(MediaType.APPLICATION_JSON).content(FormDefinitionParserTest.sampleDefinition()))
                .andExpect(jsonPath("$.problems").isEmpty());
        mockMvc.perform(admin(multipart("/api/admin/forms/{vid}/test", vid).file(answers(0)).file(photo())))
                .andExpect(status().isOk());
        mockMvc.perform(admin(post("/api/admin/forms/{vid}/publish", vid))).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private byte[] download() throws Exception {
        return mockMvc.perform(admin(get("/api/admin/backup")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private ResultActions restore(byte[] backup) throws Exception {
        MockMultipartFile file = new MockMultipartFile("backup", "backup.zip", "application/zip", backup);
        return mockMvc.perform(admin(multipart("/api/admin/backup").file(file)));
    }

    private static MockMultipartFile answers(int version) {
        String json = """
                {"version": %d, "documentId": null, "fileName": "", "answers": {
                  "name": ["백업배우"], "birth": ["1996.03.01"], "phone": ["01012345678"], "gender": ["m"],
                  "role": ["bear"]}}
                """.formatted(version);
        return new MockMultipartFile("request", "request.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    /** A plain drawn square: no real person's photo goes into the repository. */
    private static MockMultipartFile photo() throws IOException {
        var image = new java.awt.image.BufferedImage(300, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        return new MockMultipartFile("photo-photo", "portrait.png", "image/png", png.toByteArray());
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B admin(B request) {
        return request.header("Authorization", "Bearer " + NoticeFormHttpAcceptanceTest.TOKEN);
    }

    /** @param namesAndTexts entry name, then its text, repeated */
    private static byte[] zip(String... namesAndTexts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int at = 0; at < namesAndTexts.length; at += 2) {
                zip.putNextEntry(new ZipEntry(namesAndTexts[at]));
                zip.write(namesAndTexts[at + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static List<String> entries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static void deleteTree(Path folder) throws IOException {
        try (var walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = FormBackupHttpAcceptanceTest.class.getResourceAsStream(path)) {
            return input.readAllBytes();
        }
    }
}
