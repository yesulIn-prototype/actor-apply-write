package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.List;
import kr.yesulin.actor.document.CellAddress;

/** How answers reach one cell of the form. Templates name answers as {itemId}. */
public sealed interface FormOutput {
    CellAddress cell();

    /** The cell's text becomes the template, e.g. "{name} / {phone}". */
    record Text(CellAddress cell, String template, String join) implements FormOutput {}

    /** The cell keeps its own text ("성명(한글)") and the template is written under it. */
    record Append(CellAddress cell, String template, String join) implements FormOutput {}

    /** Parts of the cell's own text are swapped: "남(  )" → "남( V )", "□곰역" → "■곰역", "(   cm)" → "( {height} cm)". */
    record Edit(CellAddress cell, List<Replacement> replacements) implements FormOutput {
        public Edit {
            replacements = List.copyOf(replacements);
        }
    }

    /** A photo answer, fitted into the cell. */
    record Photo(CellAddress cell, String item) implements FormOutput {}

    /**
     * A rows answer, one table row per entry, starting at {@code cell} and going right one cell per column.
     *
     * @param columns  the item's column id for each cell from {@code cell} on; "" leaves that cell alone (번호 칸)
     * @param formRows rows the form already has for it; 0 when every row is added (below the row above {@code cell})
     * @param grow     more entries than {@code formRows} add rows to the table, copied from its last row
     */
    record Rows(CellAddress cell, String item, List<String> columns, int formRows, boolean grow) implements FormOutput {
        public Rows {
            columns = List.copyOf(columns);
        }

        /** Every cell of the form's own rows, which no other output may write. */
        List<CellAddress> formCells() {
            List<CellAddress> cells = new ArrayList<>();
            for (int row = 0; row < formRows; row++) {
                for (int column = 0; column < columns.size(); column++) {
                    cells.add(new CellAddress(cell.tableIndex(), cell.rowIndex() + row, cell.cellIndex() + column));
                }
            }
            return cells;
        }
    }

    /**
     * @param when applies only if this holds; without one it applies when every answer it names was given
     */
    record Replacement(String find, String replace, Condition when) {}

    sealed interface Condition {
        /** No condition of its own: decided by the answers the replacement names. */
        record Always() implements Condition {}

        /** "gender=m": that option is picked. */
        record Picked(String item, String option) implements Condition {}

        /** "intro": the answer is given. */
        record Answered(String item) implements Condition {}
    }
}
