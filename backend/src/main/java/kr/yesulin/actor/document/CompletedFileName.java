package kr.yesulin.actor.document;

import java.util.Locale;
import java.util.regex.Pattern;

/** A download name only; the server always stores the completed file at a fixed private path. */
public record CompletedFileName(String stem) {
    private static final Pattern FORBIDDEN = Pattern.compile("[\\p{Cntrl}\\\\/:*?\"<>|]");
    private static final Pattern RESERVED = Pattern.compile("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$");

    static CompletedFileName defaultFor(String original) {
        String stem = withoutHwp(original).replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_").strip();
        if (stem.isBlank()) {
            stem = "지원서";
        }
        return new CompletedFileName(stem.endsWith("_완성") ? stem : stem + "_완성");
    }

    static CompletedFileName chosenOrDefault(String chosen, String original) {
        if (chosen == null || chosen.isBlank()) {
            return defaultFor(original);
        }
        String stem = withoutHwp(chosen.strip());
        if (stem.isBlank() || stem.length() > 100 || stem.equals(".") || stem.equals("..")
                || stem.endsWith(".") || RESERVED.matcher(stem).matches() || FORBIDDEN.matcher(stem).find()) {
            throw new InvalidFileNameException();
        }
        return new CompletedFileName(stem);
    }

    String hwp() {
        return stem + ".hwp";
    }

    String pdf() {
        return stem + ".pdf";
    }

    private static String withoutHwp(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".hwpx")) {
            return name.substring(0, name.length() - 5);
        }
        return lower.endsWith(".hwp") ? name.substring(0, name.length() - 4) : name;
    }

    public static final class InvalidFileNameException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private InvalidFileNameException() {
            super("파일 이름은 1~100자이며 경로 기호나 제어 문자를 포함할 수 없습니다.");
        }
    }
}
