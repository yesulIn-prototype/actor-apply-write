package kr.yesulin.actor.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Persistent notice totals and hashed random browser identifiers. Never stores answers or job IDs. */
@Component
public final class UsageCounter {
    private static final Pattern OWNER = Pattern.compile("^form:([0-9]{1,12}):v[0-9]+$");
    private static final Pattern VISITOR = Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    public enum Format { HWP, PDF }
    public record Counts(long visitors, long hwp, long pdf, long unidentifiedJobs) {}
    public record Snapshot(String startedAt, Counts counts) {}
    public record StoredCounts(Set<String> visitors, long hwp, long pdf, long unidentifiedJobs) {
        public StoredCounts {
            visitors = Set.copyOf(visitors);
            if (hwp < 0 || pdf < 0 || unidentifiedJobs < 0) throw new IllegalArgumentException("집계 파일의 횟수가 음수입니다.");
        }
        Counts counts() { return new Counts(visitors.size(), hwp, pdf, unidentifiedJobs); }
    }
    public record StoredUsage(String startedAt, Map<String, StoredCounts> notices) {
        public StoredUsage {
            Instant.parse(startedAt);
            notices = Map.copyOf(notices);
        }
    }
    private static final StoredCounts EMPTY = new StoredCounts(Set.of(), 0, 0, 0);
    private final Path file;
    private final JsonMapper json;
    private StoredUsage usage;

    public UsageCounter(@Value("${yesulin.usage-file:${yesulin.forms-dir}/usage.json}") Path file, JsonMapper json)
            throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.json = json;
        usage = Files.exists(this.file) ? json.readValue(this.file.toFile(), StoredUsage.class)
                : new StoredUsage(Instant.now().toString(), Map.of());
        if (!Files.exists(this.file)) save(usage);
    }

    public synchronized Counts counts(String vid) { return usage.notices().getOrDefault(vid, EMPTY).counts(); }

    public synchronized Snapshot snapshot() {
        Set<String> visitors = new HashSet<>();
        long hwp = 0, pdf = 0, unidentified = 0;
        for (StoredCounts counts : usage.notices().values()) {
            visitors.addAll(counts.visitors());
            hwp += counts.hwp(); pdf += counts.pdf(); unidentified += counts.unidentifiedJobs();
        }
        return new Snapshot(usage.startedAt(), new Counts(visitors.size(), hwp, pdf, unidentified));
    }

    /** The caller tracks first generation per format in the expiring job, not in this analytics file. */
    public synchronized boolean record(String owner, String visitor, Format format) throws IOException {
        var match = OWNER.matcher(owner);
        if (!match.matches()) return false;
        String vid = match.group(1);
        StoredCounts previous = usage.notices().getOrDefault(vid, EMPTY);
        Set<String> visitors = new HashSet<>(previous.visitors());
        boolean identified = visitor != null && VISITOR.matcher(visitor).matches();
        if (identified && format == Format.HWP) visitors.add(hash(visitor.toLowerCase(java.util.Locale.ROOT)));
        StoredCounts next = switch (format) {
            case HWP -> new StoredCounts(visitors, previous.hwp() + 1, previous.pdf(),
                    previous.unidentifiedJobs() + (identified ? 0 : 1));
            case PDF -> new StoredCounts(visitors, previous.hwp(), previous.pdf() + 1, previous.unidentifiedJobs());
        };
        Map<String, StoredCounts> notices = new HashMap<>(usage.notices());
        notices.put(vid, next);
        StoredUsage updated = new StoredUsage(usage.startedAt(), notices);
        save(updated);
        usage = updated;
        return true;
    }

    private void save(StoredUsage value) throws IOException {
        Files.createDirectories(file.getParent());
        Path draft = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(draft, json.writeValueAsBytes(value));
        Files.move(draft, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String hash(String visitor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(visitor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }
}
