package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.CellWrite;
import kr.yesulin.actor.document.EditTarget;

/**
 * Applies a definition's output rules to an applicant's answers. A cell none of whose answers were
 * given is left as the form printed it.
 */
final class FormComposer {
    private FormComposer() {}

    /** @param cellTexts what each cell of the blank form says, for edit outputs */
    static List<CellWrite> compose(FormDefinition definition, FormAnswers answers, Map<CellAddress, String> cellTexts) {
        List<CellWrite> writes = new ArrayList<>();
        for (FormOutput output : definition.outputs()) {
            switch (output) {
                case FormOutput.Text text -> filled(text.template(), text.join(), answers)
                        .ifPresent(value -> writes.add(new CellWrite.Replace(text.cell(), value)));
                case FormOutput.Append append -> filled(append.template(), append.join(), answers)
                        .ifPresent(value -> writes.add(new CellWrite.Append(append.cell(), value)));
                case FormOutput.Photo photo -> answers.photo(photo.item())
                        .ifPresent(file -> writes.add(new CellWrite.Photo(photo.cell(), file)));
                case FormOutput.Edit edit -> edited(edit, answers, cellTexts.getOrDefault(edit.cell(), ""))
                        .ifPresent(value -> writes.add(new CellWrite.Replace(edit.cell(), value)));
            }
        }
        return writes;
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

    /** Where each output's answer is edited from the preview: the first item it names. */
    static List<EditTarget> targets(FormDefinition definition) {
        List<EditTarget> targets = new ArrayList<>();
        for (FormOutput output : definition.outputs()) {
            Optional<String> item = switch (output) {
                case FormOutput.Text text -> Placeholders.names(text.template()).stream().findFirst();
                case FormOutput.Append append -> Placeholders.names(append.template()).stream().findFirst();
                case FormOutput.Photo photo -> Optional.of(photo.item());
                case FormOutput.Edit edit -> edit.replacements().stream()
                        .flatMap(replacement -> named(replacement).stream())
                        .findFirst();
            };
            item.ifPresent(id -> targets.add(new EditTarget(id, output.cell())));
        }
        return targets;
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
