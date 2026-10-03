package kr.yesulin.actor.form;

/**
 * How the notice takes applications, shown on the applicant's finished screen: where to send, what the mail's
 * subject must say, by when. The server sends no mail; the applicant's own mail app does.
 *
 * @param subject  template like the file name's ("꼬마박사장영실_{role}_{name}"); "" for none
 * @param deadline "2026-10-07", or "" for none
 * @param note     anything else to send along ("자유곡 영상 2개"), "" for none
 */
public record Submission(String email, String subject, String deadline, String note) {
    static final Submission NONE = new Submission("", "", "", "");
}
