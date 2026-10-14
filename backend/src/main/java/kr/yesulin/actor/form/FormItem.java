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
 * @param columns   rows only: what each row asks, in order ("작품명", "역할", …)
 * @param maxRows   rows only: most rows the applicant may write
 * @param rowLabels rows only: one label per fixed form row (초등학교, 중학교, …); the k-th row always means the
 *                  k-th label and a blank one leaves that form row alone. Empty for rows the applicant adds.
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
        int max,
        List<Column> columns,
        int maxRows,
        List<String> rowLabels) {

    public enum Type {
        TEXT,
        PHONE,
        SINGLE,
        MULTI,
        PHOTO,
        /** A table of one-line entries (출연 경력): each row answers every column. */
        ROWS
    }

    /** @param output what the document gets for this pick when it differs from what the screen shows */
    public record Option(String id, String label, String output) {
        String written() {
            return output.isBlank() ? label : output;
        }
    }

    /** One column of a rows item; the applicant writes one line per row. */
    public record Column(String id, String label) {}

    public FormItem {
        options = List.copyOf(options);
        columns = List.copyOf(columns);
        rowLabels = List.copyOf(rowLabels);
    }

    Optional<Option> option(String optionId) {
        return options.stream().filter(option -> option.id().equals(optionId)).findFirst();
    }
}
