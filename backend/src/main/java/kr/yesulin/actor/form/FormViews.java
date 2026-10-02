package kr.yesulin.actor.form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.yesulin.actor.document.PreviewResponse;

/** What the form APIs send and receive. */
public final class FormViews {
    private FormViews() {}

    public enum Status {
        /** Never published. */
        DRAFT,
        /** The link opens the latest published version. */
        PUBLISHED,
        /** The operator ended the link. */
        CLOSED
    }

    /** The applicant's screen: no cells, no document text. */
    public record PublicForm(String vid, int version, String title, List<FormItem> items) {}

    /**
     * @param version    0 on the first build; afterwards the version the screen was built from
     * @param documentId the applicant's own job once it exists, so rebuilding replaces only their file
     * @param answers    by item id; choices as option ids, one or more
     */
    public record GenerateRequest(int version, UUID documentId, Map<String, List<String>> answers) {
        public GenerateRequest {
            answers = answers == null ? Map.of() : Map.copyOf(answers);
        }
    }

    public record Summary(String vid, String title, Status status, int publishedVersion, int editingVersion) {}

    /**
     * @param editingVersion   the version the editor shows: the draft, or the latest published one
     * @param editingPublished saving it starts a new draft instead of changing what applicants see
     * @param tested           the current source and definition were built successfully as a test
     */
    public record Detail(
            String vid,
            Status status,
            List<Integer> published,
            int editingVersion,
            boolean editingPublished,
            String originalName,
            boolean hasSource,
            String definition,
            List<String> problems,
            boolean tested,
            List<Cell> cells) {}

    /** One cell of the blank form, addressed the way outputs name it ("0.2.1"). */
    public record Cell(String address, String text, int row, int column, int rowSpan, int columnSpan) {}

    public record Layout(List<PreviewResponse.Page> pages, List<Box> cells) {}

    public record Box(String address, int page, double x, double y, double width, double height) {}
}
