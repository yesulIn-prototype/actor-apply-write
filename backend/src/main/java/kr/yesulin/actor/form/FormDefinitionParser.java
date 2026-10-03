package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the operator's JSON into a {@link FormDefinition}, or lists every problem found. Each answer an
 * output names must exist, and each item must be written somewhere — the document or the file name.
 */
public final class FormDefinitionParser {
    private static final Set<String> KEYS = Set.of("title", "fileName", "note", "items", "outputs", "submission");
    private static final Set<String> SUBMISSION_KEYS = Set.of("email", "subject", "deadline", "note");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private final JsonMapper json;

    public FormDefinitionParser(JsonMapper json) {
        this.json = json;
    }

    public FormDefinition parse(String text) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (JacksonException malformed) {
            throw new InvalidFormDefinitionException(List.of("JSON 형식 오류: " + malformed.getOriginalMessage()));
        }
        List<String> problems = new ArrayList<>();
        if (root == null || !root.isObject()) {
            throw new InvalidFormDefinitionException(List.of("정의는 { … } 객체여야 합니다."));
        }
        Json.unknownKeys("정의", root, KEYS, problems);
        String title = Json.text(root, "title").strip();
        if (title.isEmpty()) {
            problems.add("title: 배우 화면에 보일 공고 제목이 필요합니다.");
        }
        List<FormItem> items = new ItemParser(problems).parse(root.path("items"));
        List<FormOutput> outputs = new OutputParser(problems).parse(root.path("outputs"));
        FormDefinition definition = new FormDefinition(
                title, Json.text(root, "fileName").strip(), items, outputs, submission(root.path("submission"), problems));
        checkReferences(definition, problems);
        if (!problems.isEmpty()) {
            throw new InvalidFormDefinitionException(problems);
        }
        return definition;
    }

    private static Submission submission(JsonNode node, List<String> problems) {
        if (node.isMissingNode()) {
            return Submission.NONE;
        }
        if (!node.isObject()) {
            problems.add("submission: 제출 안내는 { email, subject, deadline, note } 객체여야 합니다.");
            return Submission.NONE;
        }
        Json.unknownKeys("submission", node, SUBMISSION_KEYS, problems);
        Submission submission = new Submission(Json.text(node, "email").strip(), Json.text(node, "subject").strip(),
                Json.text(node, "deadline").strip(), Json.text(node, "note").strip());
        if (!submission.email().isEmpty() && !EMAIL.matcher(submission.email()).matches()) {
            problems.add("submission.email: 이메일 주소 형식이 아닙니다. (현재: '" + submission.email() + "')");
        }
        if (!submission.deadline().isEmpty() && !DATE.matcher(submission.deadline()).matches()) {
            problems.add("submission.deadline: 마감일은 2026-10-07 형식이어야 합니다. (현재: '" + submission.deadline() + "')");
        }
        return submission;
    }

    private static void checkReferences(FormDefinition definition, List<String> problems) {
        Set<String> used = new HashSet<>();
        for (FormOutput output : definition.outputs()) {
            String where = "outputs (" + Cells.format(output.cell()) + ")";
            switch (output) {
                case FormOutput.Text text -> answers(definition, where, text.template(), used, problems);
                case FormOutput.Append append -> answers(definition, where, append.template(), used, problems);
                case FormOutput.Photo photo -> {
                    used.add(photo.item());
                    if (definition.item(photo.item()).filter(item -> item.type() == FormItem.Type.PHOTO).isEmpty()) {
                        problems.add(where + ": photo는 사진(photo) 항목의 id여야 합니다. (현재: '" + photo.item() + "')");
                    }
                }
                case FormOutput.Edit edit -> {
                    for (FormOutput.Replacement replacement : edit.replacements()) {
                        answers(definition, where, replacement.replace(), used, problems);
                        condition(definition, where, replacement.when(), used, problems);
                    }
                }
                case FormOutput.Rows rows -> {
                    used.add(rows.item());
                    rows(definition, where, rows, problems);
                }
            }
        }
        answers(definition, "fileName", definition.fileName(), used, problems);
        answers(definition, "submission.subject", definition.submission().subject(), used, problems);
        for (FormItem item : definition.items()) {
            if (!used.contains(item.id())) {
                problems.add("items (" + item.id() + "): 문서나 파일 이름 어디에도 쓰이지 않습니다.");
            }
        }
    }

    private static void answers(
            FormDefinition definition, String where, String template, Set<String> used, List<String> problems) {
        for (String name : Placeholders.names(template)) {
            used.add(name);
            var item = definition.item(name);
            if (item.isEmpty()) {
                problems.add(where + ": {" + name + "}에 해당하는 항목이 없습니다.");
            } else if (item.get().type() == FormItem.Type.PHOTO) {
                problems.add(where + ": 사진 항목 {" + name + "}은 글자로 쓸 수 없습니다. photo 출력을 쓰세요.");
            } else if (item.get().type() == FormItem.Type.ROWS) {
                problems.add(where + ": 줄 표 항목 {" + name + "}은 글자로 쓸 수 없습니다. rows 출력을 쓰세요.");
            }
        }
    }

    private static void rows(FormDefinition definition, String where, FormOutput.Rows rows, List<String> problems) {
        var item = definition.item(rows.item()).filter(found -> found.type() == FormItem.Type.ROWS);
        if (item.isEmpty()) {
            problems.add(where + ": rows는 줄 표(rows) 항목의 id여야 합니다. (현재: '" + rows.item() + "')");
            return;
        }
        Set<String> columns = new HashSet<>();
        item.get().columns().forEach(column -> columns.add(column.id()));
        for (String column : rows.columns()) {
            if (!column.isEmpty() && !columns.contains(column)) {
                problems.add(where + ": columns의 '" + column + "'는 " + rows.item() + " 항목의 열이 아닙니다.");
            }
        }
        if (!rows.grow() && item.get().maxRows() > rows.formRows()) {
            problems.add(where + ": 양식 줄이 " + rows.formRows() + "개라 grow 없이는 " + rows.item()
                    + " 항목의 maxRows(" + item.get().maxRows() + ")만큼 넣을 수 없습니다. maxRows를 줄이거나 grow를 켜세요.");
        }
    }

    private static void condition(
            FormDefinition definition, String where, FormOutput.Condition when, Set<String> used, List<String> problems) {
        switch (when) {
            case FormOutput.Condition.Always always -> { }
            case FormOutput.Condition.Answered answered -> {
                used.add(answered.item());
                if (definition.item(answered.item()).isEmpty()) {
                    problems.add(where + ": when의 항목 '" + answered.item() + "'이 없습니다.");
                }
            }
            case FormOutput.Condition.Picked picked -> {
                used.add(picked.item());
                boolean known = definition.item(picked.item()).flatMap(item -> item.option(picked.option())).isPresent();
                if (!known) {
                    problems.add(where + ": when '" + picked.item() + "=" + picked.option() + "'에 해당하는 선택지가 없습니다.");
                }
            }
        }
    }
}
