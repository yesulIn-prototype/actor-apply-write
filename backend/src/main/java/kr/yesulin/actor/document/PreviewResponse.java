package kr.yesulin.actor.document;

import java.util.List;

/**
 * The completed form as page images plus the areas the applicant can tap to edit.
 * Coordinates are in page units, the same as the SVG's viewBox.
 */
public record PreviewResponse(List<Page> pages, List<Hotspot> hotspots) {
    public record Page(int number, double width, double height) {}

    public record Hotspot(String fieldId, int page, double x, double y, double width, double height) {}
}
