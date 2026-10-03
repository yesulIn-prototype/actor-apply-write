package kr.yesulin.actor.form;

import java.util.List;
import java.util.Optional;

/**
 * An operator-written form: what the applicant is asked (items, in screen order) and where each answer
 * goes in the document (outputs). Nothing here is inferred from the document.
 *
 * @param fileName   template for the completed file's name, e.g. "{name}_{role}_지원서"; "" for the default
 * @param submission where and how the notice takes applications; {@link Submission#NONE} when not given
 */
public record FormDefinition(
        String title, String fileName, List<FormItem> items, List<FormOutput> outputs, Submission submission) {
    public FormDefinition {
        items = List.copyOf(items);
        outputs = List.copyOf(outputs);
    }

    Optional<FormItem> item(String id) {
        return items.stream().filter(item -> item.id().equals(id)).findFirst();
    }
}
