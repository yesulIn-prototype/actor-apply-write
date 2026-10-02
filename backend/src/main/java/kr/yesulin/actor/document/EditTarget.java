package kr.yesulin.actor.document;

/** A cell that, tapped on the preview, opens the answer with this id. */
public record EditTarget(String id, CellAddress address) {}
