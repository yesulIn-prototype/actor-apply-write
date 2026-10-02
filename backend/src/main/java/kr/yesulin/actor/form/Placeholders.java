package kr.yesulin.actor.form;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "{name} / {phone}": answers named by item id inside operator-written text. Other braces stay as they are. */
final class Placeholders {
    static final Pattern ITEM_ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,39}$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]{0,39})}");

    private Placeholders() {}

    static List<String> names(String template) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    static String fill(String template, Function<String, String> value) {
        return PLACEHOLDER.matcher(template).replaceAll(match -> Matcher.quoteReplacement(value.apply(match.group(1))));
    }
}
