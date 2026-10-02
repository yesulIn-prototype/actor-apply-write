package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellWrite;
import kr.yesulin.actor.document.EditTarget;
import kr.yesulin.actor.document.JobContent;
import kr.yesulin.actor.document.TableGrowth;

/**
 * Applies a definition's output rules to an applicant's answers. A cell none of whose answers were
 * given is left as the form printed it.
 */
final class FormComposer {
    private FormComposer() {}

    /**
     * What one build does to the blank form: rows to add, then cells to write and the cells that open each
     * answer from the preview, both addressed in the grown form. Rows an applicant added or dropped since the
     * last build move the cells below them, so this is worked out again for every build.
     *
     * @param cellTexts what each cell of the blank form says, for edit outputs
     */
    static JobContent compose(FormDefinition definition, FormAnswers answers, Map<CellAddress, String> cellTexts) {
        List<TableGrowth> growths = new ArrayList<>();
        for (FormOutput output : definition.outputs()) {
            if (output instanceof FormOutput.Rows rows && added(rows, answers) > 0) {
                growths.add(new TableGrowth(rows.cell().tableIndex(),
                        rows.cell().rowIndex() + rows.formRows() - 1, added(rows, answers)));
            }
        }
        List<CellWrite> writes = new ArrayList<>();
        List<EditTarget> targets = new ArrayList<>();
        for (FormOutput output : definition.outputs()) {
            CellAddress cell = moved(output.cell(), growths);
            switch (output) {
                case FormOutput.Text text -> filled(text.template(), text.join(), answers)
                        .ifPresent(value -> writes.add(new CellWrite.Replace(cell, value)));
                case FormOutput.Append append -> filled(append.template(), append.join(), answers)
                        .ifPresent(value -> writes.add(new CellWrite.Append(cell, value)));
                case FormOutput.Photo photo -> answers.photo(photo.item())
                        .ifPresent(file -> writes.add(new CellWrite.Photo(cell, file)));
                case FormOutput.Edit edit -> edited(edit, answers, cellTexts.getOrDefault(edit.cell(), ""))
                        .ifPresent(value -> writes.add(new CellWrite.Replace(cell, value)));
                case FormOutput.Rows rows -> {
                    // Rows added for an output with no rows of its own sit under the row above its cell, so
                    // they moved its cell too: its first row is where they start.
                    int first = cell.rowIndex() - (rows.formRows() == 0 ? added(rows, answers) : 0);
                    rows(definition, rows, answers, first, writes, targets);
                }
            }
            editedItem(output).ifPresent(id -> targets.add(new EditTarget(id, cell)));
        }
        return new JobContent(growths, writes, targets);
    }

    /** Rows beyond the form's own, when the output may add them. */
    private static int added(FormOutput.Rows rows, FormAnswers answers) {
        return rows.grow() ? Math.max(0, answers.rows(rows.item()).size() - rows.formRows()) : 0;
    }

    /** Where a blank-form cell ends up once the rows above it in its table were added. */
    private static CellAddress moved(CellAddress cell, List<TableGrowth> growths) {
        int shift = growths.stream()
                .filter(growth -> growth.tableIndex() == cell.tableIndex() && growth.belowRow() < cell.rowIndex())
                .mapToInt(TableGrowth::count)
                .sum();
        return new CellAddress(cell.tableIndex(), cell.rowIndex() + shift, cell.cellIndex());
    }

    /**
     * Writes each entry into its row; every row there is (the form's own, written or not, and the added ones)
     * opens the rows item from the preview.
     */
    private static void rows(FormDefinition definition, FormOutput.Rows rows, FormAnswers answers, int firstRow,
            List<CellWrite> writes, List<EditTarget> targets) {
        List<String> columnIds = definition.item(rows.item()).orElseThrow().columns().stream()
                .map(FormItem.Column::id)
                .toList();
        List<List<String>> entries = answers.rows(rows.item());
        int shown = rows.formRows() + added(rows, answers);
        for (int row = 0; row < shown; row++) {
            for (int at = 0; at < rows.columns().size(); at++) {
                int column = columnIds.indexOf(rows.columns().get(at));
                if (column < 0) {
                    continue;
                }
                CellAddress cell = new CellAddress(rows.cell().tableIndex(), firstRow + row, rows.cell().cellIndex() + at);
                targets.add(new EditTarget(rows.item(), cell));
                String value = row < entries.size() ? entries.get(row).get(column) : "";
                if (!value.isEmpty()) {
                    writes.add(new CellWrite.Replace(cell, value));
                }
            }
        }
    }

    /**
     * The completed file's name from the definition's template; "" when it has none or nothing was answered.
     * Several picks join with "_" ("홍길동_곰역_공주역_지원서"): a comma reads badly in a file name.
     */
    static String fileName(FormDefinition definition, FormAnswers answers) {
        return filled(definition.fileName(), "_", answers)
                .map(name -> name.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "").strip())
                .orElse("");
    }

    /** The answer an output's cell opens from the preview: the first item it names. */
    private static Optional<String> editedItem(FormOutput output) {
        return switch (output) {
            case FormOutput.Text text -> Placeholders.names(text.template()).stream().findFirst();
            case FormOutput.Append append -> Placeholders.names(append.template()).stream().findFirst();
            case FormOutput.Photo photo -> Optional.of(photo.item());
            case FormOutput.Edit edit -> edit.replacements().stream()
                    .flatMap(replacement -> named(replacement).stream())
                    .findFirst();
            // Its rows open it, cell by cell (rows above).
            case FormOutput.Rows rows -> Optional.empty();
        };
    }

    private static Optional<String> filled(String template, String join, FormAnswers answers) {
        List<String> names = Placeholders.names(template);
        if (template.isBlank() || (!names.isEmpty() && names.stream().noneMatch(answers::answered))) {
            return Optional.empty();
        }
        return Optional.of(Placeholders.fill(template, name -> answers.written(name, join)));
    }

    private static Optional<String> edited(FormOutput.Edit edit, FormAnswers answers, String original) {
        String text = original;
        boolean changed = false;
        for (FormOutput.Replacement replacement : edit.replacements()) {
            int at = text.indexOf(replacement.find());
            if (at < 0 || !applies(replacement, answers)) {
                continue;
            }
            String value = Placeholders.fill(replacement.replace(), name -> answers.written(name, ", "));
            text = text.substring(0, at) + value + text.substring(at + replacement.find().length());
            changed = true;
        }
        return changed ? Optional.of(text) : Optional.empty();
    }

    private static boolean applies(FormOutput.Replacement replacement, FormAnswers answers) {
        boolean namedGiven = Placeholders.names(replacement.replace()).stream().allMatch(answers::answered);
        return switch (replacement.when()) {
            case FormOutput.Condition.Always always -> namedGiven;
            case FormOutput.Condition.Answered answered -> answers.answered(answered.item()) && namedGiven;
            case FormOutput.Condition.Picked picked -> answers.picked(picked.item(), picked.option()) && namedGiven;
        };
    }

    private static List<String> named(FormOutput.Replacement replacement) {
        List<String> names = new ArrayList<>();
        switch (replacement.when()) {
            case FormOutput.Condition.Always always -> { }
            case FormOutput.Condition.Answered answered -> names.add(answered.item());
            case FormOutput.Condition.Picked picked -> names.add(picked.item());
        }
        names.addAll(Placeholders.names(replacement.replace()));
        return names;
    }
}
