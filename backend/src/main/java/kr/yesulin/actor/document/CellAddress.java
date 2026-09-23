package kr.yesulin.actor.document;

public record CellAddress(int tableIndex, int rowIndex, int cellIndex) {
    public CellAddress {
        if (tableIndex < 0 || rowIndex < 0 || cellIndex < 0) {
            throw new IllegalArgumentException("Cell coordinates must be non-negative");
        }
    }
}
