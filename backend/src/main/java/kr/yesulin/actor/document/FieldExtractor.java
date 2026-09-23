package kr.yesulin.actor.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import kr.yesulin.actor.document.CellText.Role;
import kr.yesulin.actor.document.FieldCandidate.FieldKind;
import kr.yesulin.actor.document.FieldCandidate.InputStyle;

/**
 * Finds the places an applicant is expected to fill, in five passes:
 * photo slots, tables (header row + rows of blanks), label → neighbouring input,
 * stand-alone templates ("성별 남( ) 여( )"), and labels that leave room in their own cell.
 * Cell meaning (blank / template / guide / label) comes from {@link CellText}.
 */
public final class FieldExtractor {
    private static final long PHOTO_MIN_HEIGHT = 7_000;
    private static final long PHOTO_MIN_WIDTH = 5_000;
    private static final Pattern NUMBER_HEADER = Pattern.compile("^(no\\.?|번호|순번|연번|#)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENDS_WITH_COLON = Pattern.compile("[:：]\\s*$");
    /** Fewer label-and-text pairs than this are ordinary two-label rows of a blank form. */
    private static final int FILLED_MIN_ANSWERS = 3;
    private static final Pattern NUMBER = Pattern.compile("^\\d{1,3}\\.?$");
    private static final Pattern OFFICE_ONLY = Pattern.compile("^(no\\.?|접수번호|수험번호|관리번호)$", Pattern.CASE_INSENSITIVE);

    public List<FieldCandidate> extract(List<CellSnapshot> cells) {
        Layout layout = new Layout(cells);
        List<FieldCandidate> fields = new ArrayList<>();
        Set<CellAddress> claimed = new HashSet<>();

        extractPhotos(layout, fields, claimed);
        extractPlacedPhotos(layout, fields, claimed);
        UnlabeledPhotoExtractor.extract(layout, fields, claimed);
        new TableExtractor(layout, fields, claimed).extract();
        extractFilledAnswers(layout, fields, claimed);
        // Every label first takes the input to its right; only then do the rest look downwards,
        // so "성명(한글)" cannot take the blank that belongs to "생년월일" in the next row.
        extractLabeledFields(layout, fields, claimed, false);
        extractLabeledFields(layout, fields, claimed, true);
        extractStandaloneTemplates(layout, fields, claimed);
        extractPromptBoxes(layout, fields, claimed);
        extractRoomyLabels(layout, fields, claimed);

        fields.sort((left, right) -> compare(left.address(), right.address()));
        return List.copyOf(fields);
    }

    private static void extractPhotos(Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        for (List<CellSnapshot> row : layout.rows()) {
            for (int index = 0; index < row.size(); index++) {
                CellSnapshot source = row.get(index);
                if (claimed.contains(source.address()) || !CellText.isPhotoLabel(source.text())) {
                    continue;
                }
                List<CellSnapshot> slots = isPhotoSized(source) ? List.of(source) : photoSlotsNear(layout, row, index, claimed);
                if (slots.isEmpty()) {
                    continue;
                }
                claimed.add(source.address());
                String label = CellText.photoLabel(source.text());
                for (CellSnapshot slot : slots) {
                    claimed.add(slot.address());
                    String name = slots.size() > 1 ? label + " " + (slots.indexOf(slot) + 1) : label;
                    fields.add(field(FieldKind.PHOTO, name, "", slot, InputStyle.BLANK, false));
                }
            }
        }
    }

    /**
     * A form completed earlier (by this app or by hand) has photos where "사진1" used to be: a photo-sized
     * cell with a picture and no text is a slot whose photo can be replaced.
     */
    private static void extractPlacedPhotos(Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        List<CellSnapshot> slots = new ArrayList<>();
        for (List<CellSnapshot> row : layout.rows()) {
            for (CellSnapshot cell : row) {
                if (cell.hasPicture() && cell.text().isBlank() && isPhotoSized(cell) && !claimed.contains(cell.address())) {
                    slots.add(cell);
                }
            }
        }
        for (CellSnapshot slot : slots) {
            claimed.add(slot.address());
            String name = slots.size() > 1 ? "사진 " + (slots.indexOf(slot) + 1) : "사진";
            fields.add(field(FieldKind.PHOTO, name, "", slot, InputStyle.BLANK, false));
        }
    }

    /**
     * Blank photo-sized boxes that belong to a photo label: to its right (in every row the label spans,
     * "프로필 사진 첨부" beside a 2×2 grid) or, failing that, beneath it ("프로필 사진" over two boxes).
     */
    private static List<CellSnapshot> photoSlotsNear(
            Layout layout, List<CellSnapshot> row, int index, Set<CellAddress> claimed) {
        CellSnapshot source = row.get(index);
        List<CellSnapshot> slots = new ArrayList<>();
        for (int offset = 0; offset < source.rowSpan(); offset++) {
            List<CellSnapshot> line = offset == 0 ? row.subList(index + 1, row.size())
                    : layout.row(source.address().tableIndex(), source.address().rowIndex() + offset).stream()
                            .filter(cell -> cell.columnAddress() > source.columnAddress()).toList();
            for (CellSnapshot cell : line) {
                if (claimed.contains(cell.address()) || !cell.text().isBlank() || !isPhotoSized(cell)) {
                    break;
                }
                slots.add(cell);
            }
        }
        if (!slots.isEmpty()) {
            return slots;
        }
        int start = source.columnAddress();
        int end = start + source.columnSpan();
        return layout.row(source.address().tableIndex(), source.address().rowIndex() + source.rowSpan()).stream()
                .filter(cell -> cell.columnAddress() >= start && cell.columnAddress() + cell.columnSpan() <= end)
                .filter(cell -> !claimed.contains(cell.address()) && cell.text().isBlank() && isPhotoSized(cell))
                .toList();
    }

    /**
     * An already completed form (uploaded again to fix or reuse it) has answers where the blanks were:
     * "이름 | 홍길동 | 생년월일 | 20000101". Those cells read as labels, so they would get a second value
     * under the old one. When several known labels sit next to such text, the text is taken as the
     * current answer: shown for editing and replaced when saved.
     */
    private static void extractFilledAnswers(Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        List<FieldCandidate> answers = new ArrayList<>();
        Set<CellAddress> used = new HashSet<>();
        Map<String, Integer> names = new HashMap<>();
        for (List<CellSnapshot> row : layout.rows()) {
            CellSnapshot label = null;
            for (CellSnapshot cell : row) {
                String qualifier = CellText.qualifier(cell.text());
                if (label != null && qualifier != null && !claimed.contains(cell.address())) {
                    // "연락처 | (집) | 01000000000": the sub-field stays a blank to fill, the answer follows it.
                    used.add(cell.address());
                    answers.add(field(FieldKind.TEXT, CellText.label(label.text()) + " (" + qualifier + ")", "",
                            cell, InputStyle.GUIDE, false));
                    continue;
                }
                if (claimed.contains(cell.address()) || CellText.role(cell.text()) != Role.LABEL
                        || CellText.isPhotoLabel(cell.text())) {
                    label = null;
                    continue;
                }
                if (CellText.isKnownLabel(cell.text())) {
                    label = cell;
                    continue;
                }
                if (label == null || CellText.normalize(cell.text()).length() > 60) {
                    continue;
                }
                String name = CellText.label(label.text());
                int count = names.merge(name, 1, Integer::sum);
                used.add(label.address());
                used.add(cell.address());
                answers.add(field(FieldKind.TEXT, count > 1 ? name + " " + count : name, CellText.hint(label.text()),
                        cell, InputStyle.FILLED, cell.text().contains("\n") || isTall(cell, label)));
            }
        }
        if (answers.stream().filter(answer -> answer.style() == InputStyle.FILLED).count() >= FILLED_MIN_ANSWERS) {
            claimed.addAll(used);
            fields.addAll(answers);
        }
    }

    private static void extractLabeledFields(
            Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed, boolean below) {
        for (List<CellSnapshot> row : layout.rows()) {
            for (int index = 0; index < row.size(); index++) {
                CellSnapshot source = row.get(index);
                if (claimed.contains(source.address()) || CellText.role(source.text()) != Role.LABEL) {
                    continue;
                }
                if (isOfficeOnly(source.text())) {
                    claimed.add(source.address());
                    inputsAfter(row, index + 1, source, claimed).forEach(cell -> claimed.add(cell.address()));
                    continue;
                }
                List<CellSnapshot> targets = new ArrayList<>();
                if (!below) {
                    targets.addAll(inputsAfter(row, index + 1, source, claimed));
                    // A label spanning rows ("학력" over two lines) owns the inputs to its right in each.
                    for (int offset = 1; offset < source.rowSpan(); offset++) {
                        List<CellSnapshot> right = layout.row(source.address().tableIndex(), source.address().rowIndex() + offset)
                                .stream().filter(cell -> cell.columnAddress() > source.columnAddress()).toList();
                        targets.addAll(inputsAfter(right, 0, source, claimed));
                    }
                } else {
                    CellSnapshot beneath = layout.beneath(source, claimed);
                    if (beneath != null && acceptsBelow(source, beneath)) {
                        targets.add(beneath);
                    }
                }
                if (targets.stream().anyMatch(target -> CellText.role(target.text()) == Role.SKIP)) {
                    claimed.add(source.address());
                    targets.forEach(target -> claimed.add(target.address()));
                    continue;
                }
                if (targets.isEmpty()) {
                    continue;
                }
                claimed.add(source.address());
                String label = CellText.label(source.text());
                CellSnapshot parent = below ? null : parentLabel(layout, source);
                for (CellSnapshot target : targets) {
                    claimed.add(target.address());
                    String name = targets.size() > 1 ? label + " " + qualifier(target, targets) : label;
                    boolean multiline = isTall(target, source) || target.text().contains("\n");
                    if (parent == null) {
                        fields.add(field(FieldKind.TEXT, name, CellText.hint(source.text()), target,
                                style(target, source), multiline));
                        continue;
                    }
                    // "희망 근무 타임" merged beside "1순위 / 2순위 / 3순위": the sub-labels only make sense under it.
                    String group = CellText.label(parent.text());
                    fields.add(tableField(FieldKind.TEXT, group + " " + name, CellText.hint(source.text()), target,
                            style(target, source), multiline, group, 1, "", name));
                }
                if (parent != null) {
                    claimed.add(parent.address());
                }
            }
        }
    }

    /**
     * A label merged over several rows directly left of {@code source} whose own neighbour is a label
     * ("희망 근무 타임 | 1순위 | …"): it names a group rather than an input of its own.
     */
    private static CellSnapshot parentLabel(Layout layout, CellSnapshot source) {
        int top = source.rowAddress();
        int bottom = top + source.rowSpan();
        CellSnapshot best = null;
        for (List<CellSnapshot> row : layout.rows()) {
            for (int index = 0; index + 1 < row.size(); index++) {
                CellSnapshot cell = row.get(index);
                boolean covers = cell.address().tableIndex() == source.address().tableIndex()
                        && cell.rowSpan() > source.rowSpan()
                        && cell.rowAddress() <= top && cell.rowAddress() + cell.rowSpan() >= bottom
                        && cell.columnAddress() + cell.columnSpan() <= source.columnAddress()
                        // A form title merged down the side ("<생활연기> 오디션 응시원서") is far wider than a label.
                        && cell.width() <= source.width() * 5 / 2;
                if (covers && CellText.role(cell.text()) == Role.LABEL && !CellText.isPhotoLabel(cell.text())
                        && CellText.role(row.get(index + 1).text()) == Role.LABEL
                        && (best == null || cell.columnAddress() > best.columnAddress())) {
                    best = cell;
                }
            }
        }
        return best;
    }

    private static List<CellSnapshot> inputsAfter(
            List<CellSnapshot> row, int start, CellSnapshot label, Set<CellAddress> claimed) {
        List<CellSnapshot> inputs = new ArrayList<>();
        for (int index = start; index < row.size(); index++) {
            CellSnapshot cell = row.get(index);
            Role role = CellText.role(cell.text());
            boolean freeFormNote = role == Role.NOTE && isTall(cell, label);
            if (claimed.contains(cell.address()) || (role == Role.LABEL) || (role == Role.NOTE && !freeFormNote)) {
                break;
            }
            inputs.add(cell);
            if (role == Role.SKIP) {
                break;
            }
        }
        return inputs;
    }

    /** The box beneath a label: an input, or a big box holding only a short note or instruction. */
    private static boolean acceptsBelow(CellSnapshot label, CellSnapshot below) {
        return switch (CellText.role(below.text())) {
            case EMPTY, TEMPLATE, GUIDE, SKIP -> true;
            case NOTE -> isTall(below, label);
            case LABEL -> isTall(below, label) && CellText.normalize(below.text()).length() <= 40
                    && !CellText.isPhotoLabel(below.text());
        };
    }

    /** "성별 / 남( ) 여( )" in one cell: the template names itself. */
    private static void extractStandaloneTemplates(Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        for (List<CellSnapshot> row : layout.rows()) {
            for (CellSnapshot cell : row) {
                if (claimed.contains(cell.address()) || CellText.role(cell.text()) != Role.TEMPLATE) {
                    continue;
                }
                String name = CellText.label(cell.text().strip().split("\\R")[0].split("[(（:：_]")[0]);
                if (!name.isBlank()) {
                    claimed.add(cell.address());
                    fields.add(field(FieldKind.TEXT, name, "", cell, InputStyle.TEMPLATE, cell.text().contains("\n")));
                }
            }
        }
    }

    /** A big box holding only "지원동기를 작성해주세요.": the request is replaced by the answer. */
    private static void extractPromptBoxes(Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        for (List<CellSnapshot> row : layout.rows()) {
            for (CellSnapshot cell : row) {
                String name = CellText.prompt(cell.text());
                if (claimed.contains(cell.address()) || name == null || cell.height() < 4_500) {
                    continue;
                }
                claimed.add(cell.address());
                fields.add(field(FieldKind.TEXT, name, "", cell, InputStyle.GUIDE, true));
            }
        }
    }

    /**
     * A label nothing else claimed, in an ordinary one-row cell, leaves room under it for the value
     * ("성명(한글)" with the name written below). Titles, section headers and full-width rows are excluded.
     */
    private static void extractRoomyLabels(Layout layout, List<FieldCandidate> fields, Set<CellAddress> claimed) {
        for (List<CellSnapshot> row : layout.rows()) {
            for (CellSnapshot cell : row) {
                boolean fullWidth = cell.columnSpan() >= layout.columnCount(cell.address().tableIndex());
                if (fullWidth && !claimed.contains(cell.address()) && CellText.role(cell.text()) == Role.LABEL
                        && ENDS_WITH_COLON.matcher(cell.text()).find()) {
                    // "지원 배역(중복 가능) :" across the whole table: the answer goes after the colon.
                    claimed.add(cell.address());
                    fields.add(field(FieldKind.TEXT, CellText.label(cell.text()), "", cell, InputStyle.TEMPLATE, false));
                    continue;
                }
                if (claimed.contains(cell.address()) || CellText.role(cell.text()) != Role.LABEL
                        || cell.rowSpan() > 1 || fullWidth || row.size() < 2 || NUMBER.matcher(cell.text().strip()).matches()) {
                    continue;
                }
                claimed.add(cell.address());
                fields.add(field(FieldKind.TEXT, CellText.label(cell.text()), CellText.hint(cell.text()), cell,
                        InputStyle.APPEND, false));
            }
        }
    }

    static InputStyle style(CellSnapshot target, CellSnapshot label) {
        return switch (CellText.role(target.text())) {
            case EMPTY -> InputStyle.BLANK;
            case TEMPLATE -> InputStyle.TEMPLATE;
            default -> InputStyle.GUIDE;
        };
    }

    private static String qualifier(CellSnapshot target, List<CellSnapshot> targets) {
        String qualifier = CellText.qualifier(target.text());
        return qualifier != null ? "(" + qualifier + ")" : String.valueOf(targets.indexOf(target) + 1);
    }

    static boolean isTall(CellSnapshot target, CellSnapshot label) {
        return target.height() >= Math.max(4_500, label.height() * 2);
    }

    static boolean isPhotoSized(CellSnapshot cell) {
        return cell.height() >= PHOTO_MIN_HEIGHT && cell.width() >= PHOTO_MIN_WIDTH;
    }

    /** "NO." / "접수번호" at the top of a form is filled in by the office. */
    private static boolean isOfficeOnly(String text) {
        return OFFICE_ONLY.matcher(CellText.normalize(text).replace(" ", "")).matches();
    }

    static boolean isNumberHeader(String text) {
        return NUMBER_HEADER.matcher(CellText.normalize(text)).matches();
    }

    static boolean isNumber(String text) {
        return NUMBER.matcher(text.strip()).matches();
    }

    static FieldCandidate field(
            FieldKind kind, String label, String hint, CellSnapshot target, InputStyle style, boolean multiline) {
        return tableField(kind, label, hint, target, style, multiline, "", 0, "", "");
    }

    static FieldCandidate tableField(
            FieldKind kind, String label, String hint, CellSnapshot target, InputStyle style, boolean multiline,
            String group, int row, String rowName, String column) {
        CellAddress address = target.address();
        String id = "%s-%d-%d-%d".formatted(
                kind.name().toLowerCase(), address.tableIndex(), address.rowIndex(), address.cellIndex());
        return new FieldCandidate(id, label, kind, address, target.text(), 1.0, "", multiline, style, hint,
                group, row, rowName, column);
    }

    private static int compare(CellAddress left, CellAddress right) {
        if (left.tableIndex() != right.tableIndex()) {
            return Integer.compare(left.tableIndex(), right.tableIndex());
        }
        if (left.rowIndex() != right.rowIndex()) {
            return Integer.compare(left.rowIndex(), right.rowIndex());
        }
        return Integer.compare(left.cellIndex(), right.cellIndex());
    }

    /** Cells grouped by physical row, with each table's column count. */
    static final class Layout {
        private final Map<List<Integer>, List<CellSnapshot>> rows = new LinkedHashMap<>();
        private final Map<Integer, Integer> columns = new HashMap<>();

        Layout(List<CellSnapshot> cells) {
            for (CellSnapshot cell : cells) {
                rows.computeIfAbsent(List.of(cell.address().tableIndex(), cell.address().rowIndex()),
                        ignored -> new ArrayList<>()).add(cell);
                columns.merge(cell.address().tableIndex(), cell.columnAddress() + cell.columnSpan(), Math::max);
            }
        }

        Iterable<List<CellSnapshot>> rows() {
            return rows.values();
        }

        List<CellSnapshot> row(int table, int row) {
            return rows.getOrDefault(List.of(table, row), List.of());
        }

        int columnCount(int table) {
            return columns.getOrDefault(table, 1);
        }

        /** The box directly beneath a cell, covering the same columns. */
        CellSnapshot beneath(CellSnapshot source, Set<CellAddress> claimed) {
            int table = source.address().tableIndex();
            List<CellSnapshot> below = row(table, source.address().rowIndex() + source.rowSpan());
            if (below.isEmpty()) {
                below = row(table, source.address().rowIndex() + 1);
            }
            return below.stream()
                    .filter(cell -> !claimed.contains(cell.address()))
                    .filter(cell -> cell.columnAddress() == source.columnAddress() && cell.columnSpan() == source.columnSpan())
                    .findFirst()
                    .orElse(null);
        }
    }
}
