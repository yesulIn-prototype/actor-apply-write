package kr.yesulin.actor.form;

import java.util.Optional;

/** A Korean phone number written the usual way: 010-1234-5678, 02-123-4567. */
final class PhoneNumber {
    private PhoneNumber() {}

    static Optional<String> format(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (!digits.startsWith("0") || digits.length() < 9 || digits.length() > 11) {
            return Optional.empty();
        }
        int area = digits.startsWith("02") ? 2 : 3;
        int last = digits.length() - 4;
        return Optional.of(digits.substring(0, area) + "-" + digits.substring(area, last) + "-" + digits.substring(last));
    }
}
