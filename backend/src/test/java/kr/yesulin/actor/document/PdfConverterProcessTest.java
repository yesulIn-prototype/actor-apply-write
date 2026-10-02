package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** How the rhwp runner treats a process that hangs or fails, with a stand-in script for rhwp. */
class PdfConverterProcessTest {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    @TempDir
    Path directory;

    @Test
    @DisplayName("rhwp가 끝나지 않으면 제한 시간에 멈추고 시간 초과로 알린다")
    void convert_StopsAtTheTimeLimit_WhenRhwpHangs() throws IOException {
        // given: a stand-in that runs for 30 seconds, and a 1 second limit
        PdfConverter converter = new PdfConverter(script("hang", WINDOWS
                ? "@\"%SystemRoot%\\System32\\ping.exe\" -n 31 127.0.0.1 >nul"
                : "sleep 30"), List.of(), Duration.ofSeconds(1));

        // when / then
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertThatThrownBy(
                () -> converter.convert(directory.resolve("in.hwp"), directory.resolve("out.pdf")))
                .isInstanceOf(HwpDocumentException.class)
                .hasMessage("문서 변환 시간이 초과되었습니다."));
    }

    @Test
    @DisplayName("rhwp가 실패하면 출력을 알려 주되 작업 ID는 가린다")
    void convert_ReportsOutputWithoutJobIds_WhenRhwpFails() throws IOException {
        // given
        String job = "7907f91f-de04-418f-a284-75836cbebce5";
        PdfConverter converter = new PdfConverter(script("fail", WINDOWS
                ? "@echo cannot read /work/" + job + "/source.hwp\r\n@exit /b 3"
                : "echo cannot read /work/" + job + "/source.hwp\nexit 3"), List.of());

        // when / then
        assertThatThrownBy(() -> converter.convert(directory.resolve("in.hwp"), directory.resolve("out.pdf")))
                .isInstanceOf(HwpDocumentException.class)
                .hasMessageContaining("cannot read /work/<job>/source.hwp")
                .message().doesNotContain(job);
        try (var left = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            assertThat(left.filter(file -> file.getFileName().toString().startsWith("rhwp-")
                    && Files.isRegularFile(file) && contains(file, job))).isEmpty();
        }
    }

    private Path script(String name, String body) throws IOException {
        Path script = directory.resolve(WINDOWS ? name + ".cmd" : name + ".sh");
        Files.writeString(script, WINDOWS ? body + "\r\n" : "#!/bin/sh\n" + body + "\n");
        if (!WINDOWS) {
            script.toFile().setExecutable(true);
        }
        return script;
    }

    private static boolean contains(Path file, String text) {
        try {
            return Files.readString(file).contains(text);
        } catch (IOException unreadable) {
            return false;
        }
    }
}
