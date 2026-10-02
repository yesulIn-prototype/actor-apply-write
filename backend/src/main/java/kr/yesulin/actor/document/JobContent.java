package kr.yesulin.actor.document;

import java.util.List;

/**
 * What one build of a notice-form job does to the blank form: rows to add, then the cells to write and the
 * cells that open each answer from the preview, both addressed in the grown form.
 */
public record JobContent(List<TableGrowth> growths, List<CellWrite> writes, List<EditTarget> targets) {
    public JobContent {
        growths = List.copyOf(growths);
        writes = List.copyOf(writes);
        targets = List.copyOf(targets);
    }
}
