package kr.yesulin.actor.document;

import java.util.regex.Pattern;

/**
 * Job ids out of log text. A job id is all it takes to download an applicant's finished file for 30 minutes,
 * so it never goes into logs: not in request paths, not in workspace paths inside error messages.
 */
public final class JobIds {
    private static final Pattern JOB_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    static final String MASK = "<job>";

    private JobIds() {}

    /** "/api/documents/7907f91f-…/completed" → "/api/documents/<job>/completed". */
    public static String masked(String text) {
        return text == null ? "" : JOB_ID.matcher(text).replaceAll(MASK);
    }
}
