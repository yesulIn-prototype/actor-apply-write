package kr.yesulin.actor.document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record StoredDocument(
        UUID id,
        String originalName,
        Path directory,
        Path source,
        Instant expiresAt,
        List<FieldCandidate> fields,
        CompletedFileName completedFileName) {}
