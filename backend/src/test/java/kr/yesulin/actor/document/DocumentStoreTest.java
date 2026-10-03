package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentStoreTest {
    @TempDir
    Path work;

    @Test
    void create_RemovesExpiredOrphan_WhenStoreStarts() throws Exception {
        // given
        Path root = work.resolve("orphan-store");
        Path orphan = root.resolve("old-document");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("source.hwp"), "expired");
        Instant createdAt = Files.readAttributes(orphan, java.nio.file.attribute.BasicFileAttributes.class)
                .creationTime().toInstant();

        // when
        new DocumentStore(root, Duration.ofMinutes(30), Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneOffset.UTC));

        // then
        assertThat(orphan).doesNotExist();
    }

    @Test
    void removeExpired_RemovesOrphan_WhenItExpiresAfterRestart() throws Exception {
        // given
        Path root = work.resolve("later-orphan-store");
        Path orphan = root.resolve("recent-document");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("source.hwp"), "recent");
        Instant createdAt = Files.readAttributes(orphan, java.nio.file.attribute.BasicFileAttributes.class)
                .creationTime().toInstant();
        MutableClock clock = new MutableClock(createdAt.plus(Duration.ofMinutes(10)));
        DocumentStore store = new DocumentStore(root, Duration.ofMinutes(30), clock);
        assertThat(orphan).exists();

        // when
        clock.now = createdAt.plus(Duration.ofMinutes(31));
        store.removeExpired();

        // then
        assertThat(orphan).doesNotExist();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
