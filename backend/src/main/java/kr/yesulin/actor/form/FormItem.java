package kr.yesulin.actor.form;

import java.util.List;
import java.util.Optional;

/**
 * One input on the applicant's screen. Settings that do not apply to a type keep their neutral value
 * (no options, 0 limits).
 *
 * @param maxLength text only: longest answer accepted
 * @param min       multi only: fewest options to pick once any is picked (or when required)
 * @param max       multi only: most options to pick, 0 for no limit
 */
public record FormItem(
        String id,
        String label,
        String help,
        boolean required,
        Type type,
        boolean multiline,
        int maxLength,
        List<Option> options,
        int min,
        int max) {

    public enum Type {
        TEXT,
        PHONE,
        SINGLE,
        MULTI,
        PHOTO
    }

    /** @param output what the document gets for this pick when it differs from what the screen shows */
    public record Option(String id, String label, String output) {
        String written() {
            return output.isBlank() ? label : output;
        }
    }

    public FormItem {
        options = List.copyOf(options);
    }

    Optional<Option> option(String optionId) {
        return options.stream().filter(option -> option.id().equals(optionId)).findFirst();
    }
}
