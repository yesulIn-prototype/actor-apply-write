package kr.yesulin.actor.form;

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
