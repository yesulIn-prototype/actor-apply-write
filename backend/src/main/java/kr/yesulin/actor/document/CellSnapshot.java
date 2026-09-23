package kr.yesulin.actor.document;

/** @param hasPicture the cell already holds a picture, e.g. the photo of a form completed earlier */
public record CellSnapshot(
        CellAddress address,
        int columnAddress,
        int rowAddress,
        int columnSpan,
        int rowSpan,
        long width,
        long height,
        String text,
        boolean hasPicture) {

    public CellSnapshot(CellAddress address, int columnAddress, int rowAddress, int columnSpan, int rowSpan,
            long width, long height, String text) {
        this(address, columnAddress, rowAddress, columnSpan, rowSpan, width, height, text, false);
    }
}
