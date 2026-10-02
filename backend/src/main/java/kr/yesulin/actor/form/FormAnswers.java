package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

/**
 * An applicant's answers, checked against the definition: required items, picks among the defined
 * options, phone numbers and lengths. Choices are kept as option ids, phone numbers already formatted.
 * A rows item arrives row by row, one value per column ("작품, 역할, 기간, 작품, 역할, 기간"); blank rows
 * are dropped.
 */
final class FormAnswers {
    private static final int ROW_CELL_LIMIT = 100;

    private final FormDefinition definition;
    private final Map<String, List<String>> values;
    private final Map<String, MultipartFile> photos;

    private FormAnswers(FormDefinition definition, Map<String, List<String>> values, Map<String, MultipartFile> photos) {
        this.definition = definition;
        this.values = values;
        this.photos = photos;
    }

    /** @param photos uploaded files by item id */
    static FormAnswers of(FormDefinition definition, Map<String, List<String>> raw, Map<String, MultipartFile> photos) {
        for (String id : raw.keySet()) {
            if (definition.item(id).filter(item -> item.type() != FormItem.Type.PHOTO).isEmpty()) {
                throw new IllegalArgumentException("양식에 없는 항목입니다: " + id);
            }
        }
        Map<String, List<String>> values = new HashMap<>();
        Map<String, MultipartFile> files = new HashMap<>();
        for (FormItem item : definition.items()) {
            if (item.type() == FormItem.Type.PHOTO) {
                MultipartFile photo = photos.get(item.id());
                if (photo != null && !photo.isEmpty()) {
                    files.put(item.id(), photo);
                } else if (item.required()) {
                    throw new InvalidAnswerException(item.label() + objectParticle(item.label()) + " 넣어주세요");
                }
                continue;
            }
            if (item.type() == FormItem.Type.ROWS) {
                List<String> rows = rows(item, raw.getOrDefault(item.id(), List.of()));
                if (rows.isEmpty() && item.required()) {
                    throw new InvalidAnswerException(item.label() + " 항목을 한 줄 이상 입력해주세요");
                }
                if (!rows.isEmpty()) {
                    values.put(item.id(), rows);
                }
                continue;
            }
            List<String> given = raw.getOrDefault(item.id(), List.of()).stream()
                    .map(value -> value == null ? "" : value.strip())
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (given.isEmpty() && item.required()) {
                throw new InvalidAnswerException(item.label() + " 항목을 " + (choice(item) ? "골라주세요" : "입력해주세요"));
            }
            if (!given.isEmpty()) {
                values.put(item.id(), accepted(item, given));
            }
        }
        return new FormAnswers(definition, Map.copyOf(values), Map.copyOf(files));
    }

    private static List<String> accepted(FormItem item, List<String> given) {
        return switch (item.type()) {
            case TEXT -> List.of(text(item, given));
            case PHONE -> List.of(PhoneNumber.format(single(item, given))
                    .orElseThrow(() -> new InvalidAnswerException(item.label() + " 번호를 확인해주세요")));
            case SINGLE -> List.of(option(item, single(item, given)));
            case MULTI -> picks(item, given);
            case PHOTO -> throw new IllegalStateException("photo answers are files, not values");
            case ROWS -> throw new IllegalStateException("rows answers are read row by row");
        };
    }

    /** The non-blank rows, flattened again; each cell one line. */
    private static List<String> rows(FormItem item, List<String> given) {
        int columns = item.columns().size();
        if (given.size() % columns != 0) {
            throw new IllegalArgumentException("줄 표의 칸 수가 맞지 않습니다: " + item.id());
        }
        List<String> kept = new ArrayList<>();
        for (int start = 0; start < given.size(); start += columns) {
            List<String> row = given.subList(start, start + columns).stream()
                    .map(value -> value == null ? "" : value.replaceAll("\\s*[\\r\\n]+\\s*", " ").strip())
                    .toList();
            if (row.stream().allMatch(String::isEmpty)) {
                continue;
            }
            if (row.stream().anyMatch(value -> value.codePointCount(0, value.length()) > ROW_CELL_LIMIT)) {
                throw new InvalidAnswerException(item.label() + " 항목의 한 칸은 " + ROW_CELL_LIMIT + "자까지 쓸 수 있어요");
            }
            kept.addAll(row);
        }
        if (kept.size() / columns > item.maxRows()) {
            throw new InvalidAnswerException(item.label() + " 항목은 " + item.maxRows() + "줄까지 쓸 수 있어요");
        }
        return List.copyOf(kept);
    }

    private static String text(FormItem item, List<String> given) {
        String text = single(item, given).replace("\r\n", "\n");
        if (!item.multiline()) {
            text = text.replaceAll("\\s*\n\\s*", " ");
        }
        if (text.codePointCount(0, text.length()) > item.maxLength()) {
            throw new InvalidAnswerException(item.label() + " 항목은 " + item.maxLength() + "자까지 쓸 수 있어요");
        }
        return text;
    }

    private static List<String> picks(FormItem item, List<String> given) {
        List<String> picked = List.copyOf(new LinkedHashSet<>(given.stream().map(id -> option(item, id)).toList()));
        if (item.max() > 0 && picked.size() > item.max()) {
            throw new InvalidAnswerException(item.label() + " 항목은 " + item.max() + "개까지 고를 수 있어요");
        }
        if (picked.size() < item.min()) {
            throw new InvalidAnswerException(item.label() + " 항목은 " + item.min() + "개 이상 골라주세요");
        }
        return picked;
    }

    private static String option(FormItem item, String id) {
        return item.option(id).map(FormItem.Option::id)
                .orElseThrow(() -> new IllegalArgumentException("양식에 없는 선택지입니다: " + item.id() + "=" + id));
    }

    private static String single(FormItem item, List<String> given) {
        if (given.size() != 1) {
            throw new IllegalArgumentException("값이 하나여야 하는 항목입니다: " + item.id());
        }
        return given.getFirst();
    }

    /** "프로필 사진을", "사진 파일를"… is wrong: the particle follows the label's last syllable. */
    private static String objectParticle(String label) {
        char last = label.charAt(label.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "을(를)";
        }
        return (last - 0xAC00) % 28 == 0 ? "를" : "을";
    }

    private static boolean choice(FormItem item) {
        return item.type() == FormItem.Type.SINGLE || item.type() == FormItem.Type.MULTI;
    }

    boolean answered(String item) {
        return values.containsKey(item) || photos.containsKey(item);
    }

    boolean picked(String item, String option) {
        return values.getOrDefault(item, List.of()).contains(option);
    }

    /** A rows item's answer: each row's values in column order. */
    List<List<String>> rows(String id) {
        FormItem item = definition.item(id).orElseThrow(() -> new IllegalArgumentException("unknown item " + id));
        List<String> flat = values.getOrDefault(id, List.of());
        int columns = item.columns().size();
        List<List<String>> rows = new ArrayList<>();
        for (int start = 0; start < flat.size(); start += columns) {
            rows.add(flat.subList(start, start + columns));
        }
        return rows;
    }

    Optional<MultipartFile> photo(String item) {
        return Optional.ofNullable(photos.get(item));
    }

    /** What the document gets for an item: the text, the formatted number, or the picked options' document text. */
    String written(String id, String join) {
        List<String> given = values.getOrDefault(id, List.of());
        FormItem item = definition.item(id).orElseThrow(() -> new IllegalArgumentException("unknown item " + id));
        if (!choice(item)) {
            return given.isEmpty() ? "" : given.getFirst();
        }
        return String.join(join, given.stream()
                .map(option -> item.option(option).map(FormItem.Option::written).orElse(option))
                .toList());
    }
}
