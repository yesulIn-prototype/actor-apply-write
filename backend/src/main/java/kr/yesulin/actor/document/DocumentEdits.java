package kr.yesulin.actor.document;

import java.util.HashSet;
import java.util.List;

/** Direct edits address the current private file, independently of the form definition. */
public final class DocumentEdits {
    public static final String TOKEN_HEADER = "X-Document-Edit-Token";
    private DocumentEdits() {}

    public record Change(String id, String text, boolean inputStyle) {
        public Change {
            if (id == null || !id.matches("[cp]:[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")
                    || text == null || text.length() > 20_000
                    || text.chars().anyMatch(code -> code < 32 && code != '\n' && code != '\r' && code != '\t')) {
                throw new Invalid("편집 위치와 내용을 확인해 주세요. 한 영역에 20,000자까지 입력할 수 있어요.");
            }
            text = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        }
    }

    public record Request(String revision, List<Change> changes) {
        public Request {
            if (revision == null || !revision.matches("[a-f0-9]{64}") || changes == null
                    || changes.isEmpty() || changes.size() > 2 || changes.stream().anyMatch(java.util.Objects::isNull)) {
                throw new Invalid("수정할 영역을 다시 선택해 주세요.");
            }
            changes = List.copyOf(changes);
            var ids = new HashSet<String>();
            if (changes.stream().anyMatch(change -> !ids.add(change.id()))) {
                throw new Invalid("서로 다른 영역을 선택해 주세요.");
            }
        }
    }

    public record Region(String id, String text, List<PageLayout.Box> boxes) {}
    public record View(String revision, List<PreviewResponse.Page> pages, List<Region> regions, String limitation) {}

    public static final class Invalid extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public Invalid(String message) { super(message); }
    }
    public static final class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public Conflict() { super("지원서가 이미 바뀌었어요. 새 미리보기에서 다시 선택해 주세요."); }
    }
    public static final class Forbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public Forbidden() { super("이 브라우저에서 만든 지원서만 직접 수정할 수 있어요."); }
    }
}
