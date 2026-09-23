package kr.yesulin.actor.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Counts completed applications. Only the number is kept — never names, files or field values. */
@Component
public final class CompletionCounter {
    private final Path file;
    private long count;

    public CompletionCounter(@Value("${yesulin.stats-file}") Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.count = read(this.file);
    }

    public synchronized long count() {
        return count;
    }

    public synchronized void increment() throws IOException {
        long next = count + 1;
        Files.createDirectories(file.getParent());
        Path draft = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(draft, Long.toString(next), StandardCharsets.UTF_8);
        Files.move(draft, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        count = next;
    }

    private static long read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        try {
            return Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).strip());
        } catch (NumberFormatException exception) {
            throw new IOException("완성 횟수 파일을 읽지 못했습니다: " + file, exception);
        }
    }
}
