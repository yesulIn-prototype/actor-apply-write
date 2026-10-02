package kr.yesulin.actor.document;

/**
 * Rows added to a table before it is filled: {@code count} copies of row {@code belowRow}, right under it.
 * Addressed in the blank form's own coordinates.
 */
public record TableGrowth(int tableIndex, int belowRow, int count) {
    public TableGrowth {
        if (tableIndex < 0 || belowRow < 0 || count < 1) {
            throw new IllegalArgumentException("A growth adds at least one row under an existing row");
        }
    }
}
