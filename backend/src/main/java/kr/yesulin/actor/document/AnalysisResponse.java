package kr.yesulin.actor.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResponse(
        UUID documentId,
        String fileName,
        Instant expiresAt,
        int tableCount,
        int cellCount,
        List<FieldCandidate> fields) {}
