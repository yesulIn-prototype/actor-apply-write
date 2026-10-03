package kr.yesulin.actor.form;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import kr.yesulin.actor.document.CellAddress;
import kr.yesulin.actor.document.HwpDocument;
import kr.yesulin.actor.document.HwpDocumentException;
import kr.yesulin.actor.document.PdfConverter;
import kr.yesulin.actor.document.RowInserter;
import kr.yesulin.actor.document.TableGrowth;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns a notice's {@link StandardSpec} into that notice's form: the standard form file with its 지원 정보 table
 * reshaped (questions added as rows, unused rows removed, the notice's title in), and the full definition that
 * matches it. What comes out is an ordinary form version: tested and published like any other.
 */
@Component
public final class StandardForms {
    static final String BASE = "standard-v1";
    static final String FILE_NAME = "지원서.hwp";
    /** 지원 정보: role, current, unavailable, then 자기소개 (rows 0–3 of table 1). */
    private static final int INFO_TABLE = 1;
    private static final int LAST_INFO_ROW = 2;
    private static final String TITLE_MARK = "{공고 제목}";

    private final PdfConverter rhwp;
    private final RowInserter rows;
    private final JsonMapper json;
    private final byte[] baseForm;
    private final ObjectNode baseDefinition;
    private final JsonNode catalog;

    public StandardForms(PdfConverter rhwp, JsonMapper json) throws IOException {
        this.rhwp = rhwp;
        this.rows = new RowInserter(rhwp);
        this.json = json;
        this.baseForm = resource("standard-v1.hwp");
        this.baseDefinition = (ObjectNode) json.readTree(resource("standard-v1.definition.json"));
        this.catalog = json.readTree(resource("standard-v1.extras.json")).path("extras");
    }

    record Expanded(byte[] form, String definition) {}

    /** A definition written as a standard-form notice names its base: {@code "base": "standard-v1"}. */
    boolean isSpec(String text) {
        try {
            JsonNode root = json.readTree(text);
            return root != null && root.isObject() && root.has("base");
        } catch (JacksonException malformed) {
            return false;
        }
    }

    Expanded expand(String specText) throws IOException, HwpDocumentException {
        List<String> problems = new ArrayList<>();
        StandardSpec spec = StandardSpec.parse(json.readTree(specText), catalog, baseIds(), problems);
        if (!spec.base().equals(BASE)) {
            problems.add("base: 지금 쓸 수 있는 기본 양식은 \"" + BASE + "\"뿐입니다.");
        }
        List<String> kept = StandardSpec.INFO_ROWS.stream().filter(row -> !spec.drop().contains(row)).toList();
        if (kept.isEmpty() && spec.extras().isEmpty()) {
            problems.add("drop: 지원 정보 줄을 모두 빼려면 추가 항목(extras)이 하나 이상 있어야 합니다. "
                    + "배우가 더하는 항목이 그 위 줄을 본떠 생기기 때문입니다.");
        }
        ObjectNode definition = definition(spec, kept, problems);
        if (!problems.isEmpty()) {
            throw new FormExceptions.NotReady(problems);
        }
        return new Expanded(form(spec, kept), json.writeValueAsString(definition));
    }

    private ObjectNode definition(StandardSpec spec, List<String> kept, List<String> problems) {
        ObjectNode definition = baseDefinition.deepCopy();
        definition.put("title", spec.title())
                .put("note", "표준 지원서 " + BASE + "에서 펼친 정의. 고칠 때는 공고 설정을 고친다.");
        if (!spec.fileName().isEmpty()) {
            definition.put("fileName", spec.fileName());
        }
        if (!spec.submission().isMissingNode()) {
            definition.set("submission", spec.submission());
        }
        ArrayNode items = json.createArrayNode();
        for (JsonNode item : definition.path("items")) {
            String id = item.path("id").asString();
            if (id.equals("more")) {
                spec.extras().forEach(items::add);
            }
            if (StandardSpec.INFO_ROWS.contains(id) && !kept.contains(id)) {
                continue;
            }
            items.add(id.equals("role") && !spec.roles().isEmpty() ? roles((ObjectNode) item, spec) : item);
        }
        Set<String> ids = new HashSet<>();
        items.forEach(item -> ids.add(item.path("id").asString()));
        spec.help().forEach((id, text) -> {
            if (!ids.contains(id)) {
                problems.add("help: '" + id + "' 항목이 없습니다.");
            }
        });
        items.forEach(item -> {
            String help = spec.help().get(item.path("id").asString());
            if (help != null) {
                ((ObjectNode) item).put("help", help.strip());
            }
        });
        definition.set("items", items);
        definition.set("outputs", outputs(definition.path("outputs"), kept, spec.extras()));
        return definition;
    }

    /** The roles to pick from: one, or up to {@code pickRoles} (0 for any number). */
    private static ObjectNode roles(ObjectNode item, StandardSpec spec) {
        ObjectNode role = item.deepCopy();
        role.remove("maxLength");
        role.put("type", spec.pickRoles() == 1 ? "single" : "multi");
        role.set("options", StandardSpec.options(spec.roles()));
        if (spec.pickRoles() > 1) {
            role.put("max", spec.pickRoles());
        }
        return role;
    }

    /** Table 1 as reshaped: the kept rows, then one row per extra, then 자기소개 (and rows applicants add above it). */
    private ArrayNode outputs(JsonNode base, List<String> kept, List<ObjectNode> extras) {
        ArrayNode outputs = json.createArrayNode();
        base.forEach(output -> {
            if (!output.path("cell").asString().startsWith(INFO_TABLE + ".")) {
                outputs.add(output);
            }
        });
        int row = 0;
        for (String id : kept) {
            outputs.add(text(row++, id));
        }
        for (ObjectNode extra : extras) {
            outputs.add(text(row++, extra.path("id").asString()));
        }
        outputs.add(json.createObjectNode().put("cell", INFO_TABLE + "." + row + ".0").put("rows", "more")
                .set("columns", json.createArrayNode().add("label").add("value")));
        ((ObjectNode) outputs.get(outputs.size() - 1)).put("formRows", 0).put("grow", true);
        outputs.add(text(row, "intro"));
        return outputs;
    }

    private ObjectNode text(int row, String id) {
        return json.createObjectNode().put("cell", INFO_TABLE + "." + row + ".1").put("text", "{" + id + "}");
    }

    /** Rows for the extras go under the last info row (as copies of it), then unused rows go, bottom first. */
    private byte[] form(StandardSpec spec, List<String> kept) throws IOException, HwpDocumentException {
        Path work = Files.createTempDirectory("yesulin-standard-");
        try {
            Path current = work.resolve("base.hwp");
            Files.write(current, baseForm);
            if (!spec.extras().isEmpty()) {
                current = rows.grow(current, work, List.of(new TableGrowth(INFO_TABLE, LAST_INFO_ROW, spec.extras().size())));
            }
            List<Integer> dropped = StandardSpec.INFO_ROWS.stream().filter(row -> !kept.contains(row))
                    .map(StandardSpec.INFO_ROWS::indexOf).sorted(Comparator.reverseOrder()).toList();
            for (int row : dropped) {
                Path next = work.resolve(UUID.randomUUID() + ".hwp");
                rhwp.deleteRow(current, next, INFO_TABLE, row);
                current = next;
            }
            Path titled = work.resolve(UUID.randomUUID() + ".hwp");
            rhwp.replaceText(current, titled, TITLE_MARK, spec.title());
            HwpDocument document = HwpDocument.open(titled);
            for (int at = 0; at < spec.extras().size(); at++) {
                document.setText(new CellAddress(INFO_TABLE, kept.size() + at, 0),
                        spec.extras().get(at).path("label").asString());
            }
            Path form = work.resolve("form.hwp");
            document.save(form);
            return Files.readAllBytes(form);
        } finally {
            try (Stream<Path> files = Files.walk(work)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private Set<String> baseIds() {
        Set<String> ids = new HashSet<>();
        baseDefinition.path("items").forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = StandardForms.class.getResourceAsStream("/standard/" + name)) {
            if (input == null) {
                throw new IOException("standard form resource missing: " + name);
            }
            return input.readAllBytes();
        }
    }
}
