package kr.yesulin.actor.document;

/**
 * A place in the form the applicant can fill.
 *
 * @param currentText what the cell holds now (template to edit, or guide to show as a placeholder)
 * @param hint        a note attached to the label, e.g. "(년도만 적어도 좋음)"
 * @param group       table this field belongs to ("학력", "공연경력"), or "" for a plain field
 * @param row         1-based row inside {@code group}, 0 for a plain field
 * @param rowName     row heading inside the table ("초등학교", "1"), may be ""
 * @param column      column heading inside the table ("공연명"), may be ""
 */
public record FieldCandidate(
        String id,
        String label,
        FieldKind kind,
        CellAddress address,
        String currentText,
        double confidence,
        String warning,
        boolean multiline,
        InputStyle style,
        String hint,
        String group,
        int row,
        String rowName,
        String column) {

    public enum FieldKind {
        TEXT,
        PHOTO
    }

    public enum InputStyle {
        /** Empty cell: write the value. */
        BLANK,
        /** Cell with inline blanks ("취미:      /특기:     "): prefill it and let the applicant edit in place. */
        TEMPLATE,
        /** Cell with an instruction or example: show it as a placeholder, replace it with the value. */
        GUIDE,
        /** The label cell itself leaves room to write ("성명(한글)"): keep the label, add the value under it. */
        APPEND,
        /** A completed form's existing answer: prefill it and replace it with the edited value. */
        FILLED
    }
}
