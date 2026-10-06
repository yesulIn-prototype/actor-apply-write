package kr.yesulin.actor.form;

import java.util.regex.Pattern;

/** A Yesulin post number ("15" from yesulin.art/posts/15): the key a shared form is found by. */
public record Vid(String value) {
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,12}$");

    public Vid {
        if (value == null || !DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException("공고 번호(vid)는 1~12자리 숫자입니다.");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
