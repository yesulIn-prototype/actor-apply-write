package kr.yesulin.actor.document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One applicant's working copy.
 *
 * @param owner   "" for a form the applicant uploaded; for a notice form, the form version it was copied from
 * @param targets the places on the page that open an answer for editing
 */
record StoredDocument(
        UUID id,
        String originalName,
        Path directory,
        Path source,
        Instant expiresAt,
        List<FieldCandidate> fields,
        CompletedFileName completedFileName,
        String owner,
        List<EditTarget> targets) {

    StoredDocument withFields(List<FieldCandidate> candidates) {
        List<EditTarget> places = candidates.stream()
                .map(field -> new EditTarget(field.id(), field.address()))
                .toList();
        return new StoredDocument(id, originalName, directory, source, expiresAt, List.copyOf(candidates),
                completedFileName, owner, places);
    }

    StoredDocument withOwner(String formVersion) {
        return new StoredDocument(id, originalName, directory, source, expiresAt, fields, completedFileName,
                formVersion, targets);
    }

    StoredDocument withTargets(List<EditTarget> places) {
        return new StoredDocument(id, originalName, directory, source, expiresAt, fields, completedFileName,
                owner, List.copyOf(places));
    }

    StoredDocument withCompletedFileName(CompletedFileName name) {
        return new StoredDocument(id, originalName, directory, source, expiresAt, fields, name, owner, targets);
    }

    Path completedHwp() {
        return directory.resolve("completed.hwp");
    }

    Path completedPdf() {
        return directory.resolve("completed.pdf");
    }
}
