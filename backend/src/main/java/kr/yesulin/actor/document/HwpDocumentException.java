package kr.yesulin.actor.document;

public final class HwpDocumentException extends Exception {
    private static final long serialVersionUID = 1L;

    public HwpDocumentException(String message) {
        super(message);
    }

    public HwpDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
