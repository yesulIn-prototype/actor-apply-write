package kr.yesulin.actor.document;

/** What a system browser needs to reopen a form finished inside an in-app browser: its saved file name. */
public record ResumeResponse(String documentId, String fileName, boolean completed) {}
