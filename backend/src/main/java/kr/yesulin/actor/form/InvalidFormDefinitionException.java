package kr.yesulin.actor.form;

import java.util.List;

/** The operator's definition cannot be used; every problem found is listed for fixing at once. */
public final class InvalidFormDefinitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient List<String> problems;

    InvalidFormDefinitionException(List<String> problems) {
        super(String.join(" / ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
