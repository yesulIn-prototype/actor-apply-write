package kr.yesulin.actor.form;

/** An answer the form does not accept; the message is shown to the applicant as is. */
public final class InvalidAnswerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidAnswerException(String message) {
        super(message);
    }
}
