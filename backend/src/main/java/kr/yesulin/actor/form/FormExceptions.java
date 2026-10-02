package kr.yesulin.actor.form;

import java.util.List;

/** Why a notice form cannot be used right now; each maps to its own API error code. */
public final class FormExceptions {
    private FormExceptions() {}

    /** No published form for this notice (never published, or the number is wrong). */
    public static final class NotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotFound() {
            super("이 공고의 지원서 작성 링크를 찾을 수 없습니다.");
        }
    }

    /** The operator ended the link: no new applications. */
    public static final class Closed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Closed() {
            super("지원서 작성이 마감된 공고입니다.");
        }
    }

    /** The screen was built from a version that was never published for this notice. */
    public static final class Changed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Changed() {
            super("지원서 양식이 바뀌었습니다. 새로고침해 주세요.");
        }
    }

    /** The draft cannot be tested or published yet; the operator sees every reason. */
    public static final class NotReady extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient List<String> problems;

        NotReady(List<String> problems) {
            super(String.join(" / ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> problems() {
            return problems;
        }
    }

    /** An uploaded backup that is not one of ours, or is damaged; nothing from it was restored. */
    public static final class InvalidBackup extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidBackup(String message) {
            super(message);
        }
    }
}
