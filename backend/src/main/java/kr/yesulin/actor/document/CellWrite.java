package kr.yesulin.actor.document;

import org.springframework.web.multipart.MultipartFile;

/** What goes into one cell of the applicant's copy. */
public sealed interface CellWrite {
    CellAddress address();

    /** Replaces what the cell says, keeping its formatting. */
    record Replace(CellAddress address, String text) implements CellWrite {}

    /** Keeps what the cell says ("성명(한글)") and writes under it. */
    record Append(CellAddress address, String text) implements CellWrite {}

    /** Puts a photo into the cell, sized to fit. */
    record Photo(CellAddress address, MultipartFile image) implements CellWrite {}
}
