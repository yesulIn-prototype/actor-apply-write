package kr.yesulin.actor.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompletionCounterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("완성 횟수는 서버를 다시 켜도 이어진다")
    void increment_PersistsAcrossRestarts() throws Exception {
        // given
        Path file = temporaryDirectory.resolve("stats/completed-count.txt");
        CompletionCounter first = new CompletionCounter(file);

        // when
        first.increment();
        first.increment();

        // then
        assertThat(first.count()).isEqualTo(2);
        assertThat(new CompletionCounter(file).count()).isEqualTo(2);
    }
}
