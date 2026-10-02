package kr.yesulin.actor.form;

import kr.yesulin.actor.document.CellAddress;

/** The "표.행.칸" spelling of a cell address that the operator writes and the cell list shows. */
final class Cells {
    private Cells() {}

    static String format(CellAddress cell) {
        return cell.tableIndex() + "." + cell.rowIndex() + "." + cell.cellIndex();
    }
}
