package kr.yesulin.actor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class DocumentStore {
    private static final Logger log = LoggerFactory.getLogger(DocumentStore.class);
    /** Uploads kept at once; with 20 MB forms and photos this bounds disk use. */
    private static final int DEFAULT_CAPACITY = 300;
    private final Path root;
    private final int capacity;
    private final Duration ttl;
    private final Clock clock;
    private final Map<UUID, StoredDocument> documents = new ConcurrentHashMap<>();

    @Autowired
    public DocumentStore(
            @Value("${yesulin.workspace}") String workspace,
            @Value("${yesulin.document-ttl}") Duration ttl,
            @Value("${yesulin.max-documents:" + DEFAULT_CAPACITY + "}") int capacity) throws IOException {
        this(Path.of(workspace), ttl, Clock.systemUTC(), capacity);
    }

    DocumentStore(Path root, Duration ttl, Clock clock) throws IOException {
        this(root, ttl, clock, DEFAULT_CAPACITY);
    }

    DocumentStore(Path root, Duration ttl, Clock clock, int capacity) throws IOException {
        this.capacity = capacity;
        this.root = root.toAbsolutePath().normalize();
        this.ttl = ttl;
        this.clock = clock;
        Files.createDirectories(this.root);
        removeExpiredOrphans();
    }

    StoredDocument create(String originalName, byte[] content) throws IOException {
        if (documents.size() >= capacity) {
            removeExpired();
            if (documents.size() >= capacity) {
                log.warn("store full documents={} capacity={}", documents.size(), capacity);
                throw new StoreFullException();
            }
        }
        UUID id = UUID.randomUUID();
        Path directory = root.resolve(id.toString()).normalize();
        ensureInsideRoot(directory);
        Files.createDirectories(directory);
        Path source = directory.resolve("source.hwp");
        Files.write(source, content);
        StoredDocument stored = new StoredDocument(
                id, safeFileName(originalName), directory, source, clock.instant().plus(ttl), java.util.List.of(),
                CompletedFileName.defaultFor(safeFileName(originalName)));
        documents.put(id, stored);
        return stored;
    }

    StoredDocument attachFields(UUID id, java.util.List<FieldCandidate> fields) {
        StoredDocument current = require(id);
        StoredDocument updated = new StoredDocument(
                current.id(), current.originalName(), current.directory(), current.source(),
                current.expiresAt(), java.util.List.copyOf(fields), current.completedFileName());
        documents.put(id, updated);
        return updated;
    }

    void attachCompletedFileName(UUID id, CompletedFileName fileName) {
        StoredDocument current = require(id);
        documents.put(id, new StoredDocument(
                current.id(), current.originalName(), current.directory(), current.source(),
                current.expiresAt(), current.fields(), fileName));
    }

    StoredDocument require(UUID id) {
        StoredDocument document = documents.get(id);
        if (document == null || !document.expiresAt().isAfter(clock.instant())) {
            if (document != null) {
                remove(id);
            }
            throw new DocumentNotFoundException();
        }
        return document;
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void removeExpired() {
        Instant now = clock.instant();
        var expired = documents.values().stream()
                .filter(document -> !document.expiresAt().isAfter(now))
                .map(StoredDocument::id)
                .toList();
        expired.forEach(this::remove);
        if (!expired.isEmpty()) {
            log.info("expired documents removed={} remaining={}", expired.size(), documents.size());
        }
        try {
            removeExpiredOrphans();
        } catch (IOException exception) {
            // The next scheduled cleanup retries directories that are temporarily locked.
            log.warn("orphan cleanup failed: {}", exception.toString());
        }
    }

    void remove(UUID id) {
        StoredDocument document = documents.get(id);
        if (document == null) {
            return;
        }
        try {
            deleteDirectory(document.directory());
            documents.remove(id, document);
        } catch (IOException exception) {
            // A later cleanup cycle can retry files held by another local process.
            log.warn("delete failed document={}: {}", id, exception.toString());
        }
    }

    private void removeExpiredOrphans() throws IOException {
        Instant cutoff = clock.instant().minus(ttl);
        try (var entries = Files.list(root)) {
            for (Path entry : entries.filter(Files::isDirectory).toList()) {
                Instant createdAt = Files.readAttributes(entry, BasicFileAttributes.class)
                        .creationTime()
                        .toInstant();
                if (createdAt.isBefore(cutoff)) {
                    deleteDirectory(entry);
                }
            }
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        ensureInsideRoot(normalizedDirectory);
        try (var paths = Files.walk(normalizedDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(normalizedDirectory)) {
                    throw new IOException("작업공간 밖의 파일은 삭제할 수 없습니다.");
                }
                Files.deleteIfExists(normalized);
            }
        }
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("작업공간 밖의 경로는 사용할 수 없습니다.");
        }
    }

    private static String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "지원서.hwp";
        }
        return Path.of(originalName).getFileName().toString();
    }

    public static final class StoreFullException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        StoreFullException() {
            super("지금 사용하는 사람이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    public static final class DocumentNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DocumentNotFoundException() {
            super("문서를 찾을 수 없거나 보관 시간이 지났습니다. 다시 업로드해 주세요.");
        }
    }
}
