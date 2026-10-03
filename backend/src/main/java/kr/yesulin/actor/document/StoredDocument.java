package kr.yesulin.actor.document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One applicant's working copy.
 *
 * @param owner   the notice form version this applicant's job was copied from
 * @param targets the places on the page that open an answer for editing
 */
record StoredDocument(
        UUID id,
        String originalName,
        Path directory,
        Path source,
        Instant expiresAt,
        CompletedFileName completedFileName,
        String owner,
        List<EditTarget> targets) {

    StoredDocument withOwner(String formVersion) {
        return new StoredDocument(id, originalName, directory, source, expiresAt, completedFileName,
                formVersion, targets);
    }

    StoredDocument withTargets(List<EditTarget> places) {
        return new StoredDocument(id, originalName, directory, source, expiresAt, completedFileName,
                owner, List.copyOf(places));
    }

    StoredDocument withCompletedFileName(CompletedFileName name) {
        return new StoredDocument(id, originalName, directory, source, expiresAt, name, owner, targets);
    }

    Path completedHwp() {
        return directory.resolve("completed.hwp");
    }

    Path completedPdf() {
        return directory.resolve("completed.pdf");
    }
}
