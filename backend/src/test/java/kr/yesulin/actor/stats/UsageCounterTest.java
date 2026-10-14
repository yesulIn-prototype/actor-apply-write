package kr.yesulin.actor.stats;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UsageCounterTest {
    @TempDir Path folder;

    @Test
    void record_PersistsCountsAndDeduplicatesBrowsersWithoutRawIdentifiers() throws Exception {
        // given
        Path file = folder.resolve("usage.json");
        var json = JsonMapper.builder().build();
        var usage = new UsageCounter(file, json);
        String visitor = "00000000-0000-4000-8000-000000000011";
        // when
        usage.record("form:123:v1", visitor, UsageCounter.Format.HWP);
        usage.record("form:123:v2", visitor, UsageCounter.Format.HWP);
        usage.record("form:124:v1", visitor, UsageCounter.Format.HWP);
        usage.record("form:123:v1", null, UsageCounter.Format.PDF);
        usage.record("test:123:v1", visitor, UsageCounter.Format.HWP);
        usage.record("form:125:v1", null, UsageCounter.Format.HWP);
        usage.recordView("123");
        usage.recordView("123");
        usage.recordView("126");
        // then
        var reloaded = new UsageCounter(file, json);
        assertThat(reloaded.snapshot().counts()).isEqualTo(new UsageCounter.Counts(1, 4, 1, 1, 3));
        assertThat(reloaded.counts("123")).isEqualTo(new UsageCounter.Counts(1, 2, 1, 0, 2));
        assertThat(reloaded.counts("126")).isEqualTo(new UsageCounter.Counts(0, 0, 0, 0, 1));
        assertThat(Files.readString(file)).doesNotContain(visitor, "form:", "test:");
        assertThat(reloaded.snapshot().startedAt()).isEqualTo(usage.snapshot().startedAt());
    }

    @Test
    void counter_ReadsFileWrittenBeforeViews_AsZeroViews() throws Exception {
        // given
        Path file = folder.resolve("usage.json");
        Files.writeString(file, """
                {"startedAt":"2026-10-05T00:00:00Z","notices":{"54":{"visitors":[],"hwp":1,"pdf":0,"unidentifiedJobs":1}}}
                """);
        var usage = new UsageCounter(file, JsonMapper.builder().build());
        // when
        usage.recordView("54");
        // then
        assertThat(usage.counts("54")).isEqualTo(new UsageCounter.Counts(0, 1, 0, 1, 1));
    }
}
