package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the operator's JSON into a {@link FormDefinition}, or lists every problem found. Each answer an
 * output names must exist, and each item must be written somewhere — the document or the file name.
 */
public final class FormDefinitionParser {
    private static final Set<String> KEYS = Set.of("title", "fileName", "note", "items", "outputs");
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
        FormDefinition definition = new FormDefinition(title, Json.text(root, "fileName").strip(), items, outputs);
        checkReferences(definition, problems);
        if (!problems.isEmpty()) {
            throw new InvalidFormDefinitionException(problems);
        }
        return definition;
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
            }
        }
        answers(definition, "fileName", definition.fileName(), used, problems);
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
            }
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
