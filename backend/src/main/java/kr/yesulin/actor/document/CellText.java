package kr.yesulin.actor.document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads what a cell's text means on an application form. */
final class CellText {
    enum Role {
        /** Nothing written. */
        EMPTY,
        /** A field name: "이름", "키/몸무게". */
        LABEL,
        /** Text with inline blanks to fill in place: "취미:      /특기:     ", "(      ) 초등학교". */
        TEMPLATE,
        /** An instruction or example that the value replaces: "년  월  일", "(구체주소를…)", "00.00.00~00.00.00". */
        GUIDE,
        /** Must stay empty: "(공란으로 비워 주시기 바랍니다)". */
        SKIP,
        /** Long prose that is neither a label nor a guide. */
        NOTE
    }

    private static final int LABEL_MAX_LENGTH = 80;
    private static final Pattern DO_NOT_FILL = Pattern.compile("공란|비워주|비워두|기재하지마|작성하지마|쓰지마|사무국기재|담당자기재");
    private static final Pattern UNIT_ONLY = Pattern.compile("^(cm|kg|㎝|㎏|세|살|원|명|개월|년|학년)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXAMPLE = Pattern.compile("https?://|www\\.|예\\)|예:|ex\\)|e\\.g\\.|예시", Pattern.CASE_INSENSITIVE);
    /** Units, dates and punctuation that make up a guide such as "( 년 월 일 )" or "cm / kg". */
    private static final Pattern GUIDE_TOKENS = Pattern.compile(
            "[\\s()（）\\[\\]{}<>「」『』:：/／\\\\\\-–—~.,，·_＿□■☐☑☒○●◯◎※*@]|년|월|일|세|살|만|개월|cm|kg|㎝|㎏|mm|원|시|분",
            Pattern.CASE_INSENSITIVE);
    /** A guide is laid out with gaps or marks; a bare word like "일시" is a label. */
    private static final Pattern GUIDE_LAYOUT = Pattern.compile(".*[\\s()（）\\[\\]/:：\\-_.·□○~@].*", Pattern.DOTALL);
    /** "00.00.00~00.00.00", "0000-00-00": a format example. */
    private static final Pattern ZERO_EXAMPLE = Pattern.compile("^[0Xx\\s.\\-~/:()]*0[0Xx\\s.\\-~/:()]*$");
    private static final Pattern BLANK_MARKERS = Pattern.compile(
            "[(（]\\s*[)）]|[(（]\\s{2,}|\\s{2,}[)）]|[:：]\\s{3,}|_{3,}|＿{2,}|[□☐]|[.·…]{5,}");
    private static final Pattern GENDER_CHOICE = Pattern.compile("^남\\s*[/,·]?\\s*여$");
    /** "(집)", "(핸드폰) --": names a sub-field and leaves the rest of the cell to fill. */
    private static final Pattern QUALIFIER = Pattern.compile("^[(（]\\s*([^()（）]{1,10}?)\\s*[)）]\\s*[-–—~]*\\s*$");
    private static final Pattern INSTRUCTION = Pattern.compile(
            "^(?:[(（\\[［<].*[)）\\]］>]|[※*].*|\\[\\*.*|X .* X)$", Pattern.DOTALL);
    /** Field names common to audition forms; text next to one of these in a completed form is its answer. */
    private static final Pattern KNOWN_LABEL = Pattern.compile(
            "^(?:이름|성명|생년월일|생일|나이|연령|성별|주소|거주지|연락처|전화|휴대폰|핸드폰|이메일|e-?mail|메일|"
                    + "희망배역|지원배역|배역|지원분야|최종학력|학력|학교|전공|키|몸무게|체중|신장|취미|특기|sns|인스타|"
                    + "소속|경력|혈액형|병역|mbti|국적|직업|자기소개|지원동기|영문|한문|한글)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTION = Pattern.compile("여부|있습니까|\\?|기입|기재");
    /** "지원동기를 작성해주세요.": the box asks for its own content. */
    private static final Pattern PROMPT = Pattern.compile(
            "^(.{2,30}?)\\s*(?:을|를|이|가|에 대해|에 대하여)?\\s*(?:자유롭게\\s*)?(?:작성|기재|적어|써)\\s*(?:해)?\\s*주(?:세요|십시오|시기 바랍니다)[.!]?$");
    private static final Pattern PHOTO_WORD = Pattern.compile("사진|프로필|얼굴|상반신|전신|포토|photo", Pattern.CASE_INSENSITIVE);
    private static final Pattern LETTER_SPACED = Pattern.compile("(?<![가-힣])[가-힣](?: [가-힣]){2,}(?![가-힣])");
    private static final Pattern WHOLLY_SPACED = Pattern.compile("^[가-힣](?: [가-힣])+$");

    private CellText() {}

    static Role role(String raw) {
        String text = raw.strip();
        if (text.isEmpty()) {
            return Role.EMPTY;
        }
        String compact = text.replaceAll("\\s+", "");
        if (DO_NOT_FILL.matcher(compact).find()) {
            return Role.SKIP;
        }
        if (UNIT_ONLY.matcher(compact).matches() || EXAMPLE.matcher(text).find()
                || ZERO_EXAMPLE.matcher(text).matches() || QUALIFIER.matcher(text).matches()) {
            return Role.GUIDE;
        }
        String rest = GUIDE_TOKENS.matcher(text).replaceAll("");
        if (rest.isEmpty() && GUIDE_LAYOUT.matcher(text).matches()) {
            return Role.GUIDE;
        }
        // "0000년 00월 00일 (00세)": a date format with zeros in place of the numbers.
        if (!rest.isEmpty() && rest.chars().allMatch(ch -> ch == '0' || ch == 'X' || ch == 'x')
                && !rest.equals(compact)) {
            return Role.GUIDE;
        }
        if (BLANK_MARKERS.matcher(text).find() || GENDER_CHOICE.matcher(compact).matches()) {
            return Role.TEMPLATE;
        }
        if (INSTRUCTION.matcher(text).matches()) {
            return Role.GUIDE;
        }
        return normalize(text).length() > LABEL_MAX_LENGTH ? Role.NOTE : Role.LABEL;
    }

    static boolean isKnownLabel(String raw) {
        String compact = normalize(raw).replaceAll("[\\s()（）\\[\\]<>*:：]", "");
        return KNOWN_LABEL.matcher(compact).find() || QUESTION.matcher(raw).find();
    }

    /** "지원동기를 작성해주세요." → "지원동기", else null. */
    static String prompt(String raw) {
        Matcher matcher = PROMPT.matcher(normalize(raw).strip());
        return matcher.matches() ? matcher.group(1).strip() : null;
    }

    /** "(집)" / "(핸드폰) --" → "집" / "핸드폰", else null. */
    static String qualifier(String raw) {
        Matcher matcher = QUALIFIER.matcher(raw.strip());
        return matcher.matches() ? matcher.group(1).strip() : null;
    }

    static boolean isPhotoLabel(String raw) {
        String first = normalize(firstLine(raw)).replaceAll("[()（）\\[\\]<>\\s]", "");
        return !first.isEmpty() && first.length() <= 20 && PHOTO_WORD.matcher(first).find();
    }

    /** Label shown to the applicant: joined lines, letter-spacing removed, trailing notes moved to {@link #hint}. */
    static String label(String raw) {
        List<String> lines = lines(raw);
        if (lines.size() > 1 && lines.subList(1, lines.size()).stream().allMatch(CellText::isParenthesized)) {
            return trimLabel(lines.getFirst());
        }
        return trimLabel(String.join(" ", lines));
    }

    static String hint(String raw) {
        List<String> lines = lines(raw);
        if (lines.size() > 1 && lines.subList(1, lines.size()).stream().allMatch(CellText::isParenthesized)) {
            return String.join(" ", lines.subList(1, lines.size()));
        }
        return "";
    }

    static String photoLabel(String raw) {
        return trimLabel(normalize(normalize(firstLine(raw)).replaceAll("^[(（\\[<]\\s*|\\s*[)）\\]>]$", "")));
    }

    /** Words left in a template once its blanks are removed: "(      ) 초등학교" → "초등학교". */
    static String templateWords(String raw) {
        return normalize(raw.replaceAll("[(（][^)）]*[)）]|[_＿:：/]", " "));
    }

    /**
     * Collapses whitespace and letter-spacing used for alignment: "이 름" → "이름", "자 기 소 개 글 (…)" → "자기소개글 (…)".
     * Two spaced syllables inside longer text ("그 외 자격사항") are real words and stay apart.
     */
    static String normalize(String text) {
        String collapsed = text.replaceAll("\\s+", " ").strip();
        if (WHOLLY_SPACED.matcher(collapsed).matches()) {
            return collapsed.replace(" ", "");
        }
        Matcher matcher = LETTER_SPACED.matcher(collapsed);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, matcher.group().replace(" ", ""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String trimLabel(String text) {
        return text.replaceAll("[\\s:：*]+$", "").strip();
    }

    /** Lines under a label that are notes rather than part of its name: "(년도만 적어도 좋음)", "1. 년도". */
    private static boolean isParenthesized(String line) {
        return line.startsWith("(") || line.startsWith("（") || line.matches("^\\d+\\..*");
    }

    private static String firstLine(String raw) {
        List<String> lines = lines(raw);
        return lines.isEmpty() ? "" : lines.getFirst();
    }

    private static List<String> lines(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            String normalized = normalize(line);
            if (!normalized.isEmpty()) {
                lines.add(normalized);
            }
        }
        return lines;
    }
}
