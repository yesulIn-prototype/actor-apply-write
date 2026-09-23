package kr.yesulin.actor.document;

/**
 * Size of a photo inside a cell, in HWP units.
 * Priority 1 keeps the photo's own aspect ratio; priority 2 makes it as large as the cell allows.
 */
record PhotoPlacement(long width, long height) {
    static PhotoPlacement contain(int imageWidth, int imageHeight, long areaWidth, long areaHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("사진 크기를 확인할 수 없습니다.");
        }
        long width = Math.max(1, areaWidth);
        long height = Math.max(1, areaHeight);
        double scale = Math.min((double) width / imageWidth, (double) height / imageHeight);
        return new PhotoPlacement(
                Math.max(1, Math.min(width, Math.round(imageWidth * scale))),
                Math.max(1, Math.min(height, Math.round(imageHeight * scale))));
    }
}
