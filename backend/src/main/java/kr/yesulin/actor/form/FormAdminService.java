package kr.yesulin.actor.form;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.yesulin.actor.document.CellSnapshot;
import kr.yesulin.actor.document.DocumentService;
import kr.yesulin.actor.document.HwpDocument;
import kr.yesulin.actor.document.HwpDocumentException;
import kr.yesulin.actor.document.PageLayout;
import kr.yesulin.actor.document.PreviewService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * The operator's side: upload a notice's form, write its definition, test-build it, publish it.
 * Publishing needs a definition without problems and a successful test build of exactly that state.
 */
@Service
public final class FormAdminService {
    private static final int LONGEST_DEFINITION = 200_000;
    private final FormStore store;
    private final FormBuilder builder;
    private final DocumentService documents;
    private final PreviewService previews;

    public FormAdminService(FormStore store, FormBuilder builder, DocumentService documents, PreviewService previews) {
        this.store = store;
        this.builder = builder;
        this.documents = documents;
        this.previews = previews;
    }

    public List<FormViews.Summary> list() throws IOException {
        List<FormViews.Summary> summaries = new ArrayList<>();
        for (Vid vid : store.all()) {
            List<Integer> versions = store.versions(vid);
            FormStore.State state = store.state(vid);
            int editing = versions.isEmpty() ? 0 : versions.getLast();
            summaries.add(new FormViews.Summary(vid.value(), title(vid, editing), status(state),
                    state.current().orElse(0), editing));
        }
        return summaries;
    }

    public FormViews.Detail detail(Vid vid) throws IOException, HwpDocumentException {
        List<Integer> versions = store.versions(vid);
        FormStore.State state = store.state(vid);
        if (versions.isEmpty()) {
            return new FormViews.Detail(vid.value(), status(state), state.published(), 0, false, "", false, "",
                    List.of(), false, List.of());
        }
        int editing = versions.getLast();
        FormStore.VersionInfo info = store.info(vid, editing);
        var source = store.source(vid, editing);
        List<CellSnapshot> cells = source.isPresent() ? HwpDocument.open(source.get()).cells() : List.of();
        boolean tested = !info.testedHash().isEmpty() && info.testedHash().equals(builder.fingerprint(vid, editing));
        return new FormViews.Detail(vid.value(), status(state), state.published(), editing,
                state.published().contains(editing), info.originalName(), source.isPresent(),
                store.definition(vid, editing).orElse(""), problems(vid, editing, cells), tested,
                cells.stream().map(FormAdminService::cell).toList());
    }

    public FormViews.Detail uploadSource(Vid vid, MultipartFile upload) throws IOException, HwpDocumentException {
        byte[] hwp = documents.hwpSource(upload);
        readable(hwp);
        int draft = store.draft(vid);
        String name = upload.getOriginalFilename() == null ? "지원서.hwp" : Path.of(upload.getOriginalFilename()).getFileName().toString();
        store.saveSource(vid, draft, name, hwp);
        PreviewService.deleteRendering(store.renderDirectory(vid, draft));
        return detail(vid);
    }

    public FormViews.Detail saveDefinition(Vid vid, String definition) throws IOException, HwpDocumentException {
        if (definition.length() > LONGEST_DEFINITION) {
            throw new IllegalArgumentException("양식 정의가 너무 깁니다.");
        }
        store.saveDefinition(vid, store.draft(vid), definition);
        return detail(vid);
    }

    /** The blank form's pages with every cell's box, so the operator can read off cell addresses. */
    public FormViews.Layout layout(Vid vid) throws IOException, HwpDocumentException {
        int editing = editing(vid);
        PageLayout layout = previews.layout(builder.source(vid, editing), store.renderDirectory(vid, editing));
        List<FormViews.Box> boxes = new ArrayList<>();
        layout.cells().forEach((address, placed) -> placed.forEach(box -> boxes.add(new FormViews.Box(
                Cells.format(address), box.page(), box.x(), box.y(), box.width(), box.height()))));
        return new FormViews.Layout(layout.pages(), boxes);
    }

    public byte[] layoutPage(Vid vid, int page) throws IOException, HwpDocumentException {
        layout(vid);
        return previews.pageImage(store.renderDirectory(vid, editing(vid)), page);
    }

    /** The applicant's screen for the version being edited, to fill in test answers. */
    public FormViews.PublicForm editingForm(Vid vid) throws IOException {
        int editing = editing(vid);
        FormDefinition definition = builder.definition(vid, editing);
        return new FormViews.PublicForm(vid.value(), editing, definition.title(), definition.items());
    }

    public FormBuilder.Built test(Vid vid, FormViews.GenerateRequest request, Map<String, MultipartFile> photos)
            throws IOException, HwpDocumentException {
        int editing = editing(vid);
        FormBuilder.Built built = builder.build(vid, editing, "test:" + vid.value() + ":v" + editing, request, photos, false);
        List<CellSnapshot> cells = HwpDocument.open(builder.source(vid, editing)).cells();
        if (problems(vid, editing, cells).isEmpty()) {
            store.markTested(vid, editing, builder.fingerprint(vid, editing));
        }
        return built;
    }

    public FormViews.Detail publish(Vid vid) throws IOException, HwpDocumentException {
        int editing = editing(vid);
        if (!store.state(vid).published().contains(editing)) {
            FormViews.Detail detail = detail(vid);
            List<String> reasons = new ArrayList<>(detail.problems());
            if (!detail.tested()) {
                reasons.add("지금 내용으로 테스트 생성을 한 번 성공해야 공개할 수 있습니다.");
            }
            if (!reasons.isEmpty()) {
                throw new FormExceptions.NotReady(reasons);
            }
            store.publish(vid, editing);
        }
        return detail(vid);
    }

    public FormViews.Detail close(Vid vid, boolean closed) throws IOException, HwpDocumentException {
        if (store.state(vid).published().isEmpty()) {
            throw new FormExceptions.NotReady(List.of("공개한 적이 없는 공고입니다."));
        }
        store.close(vid, closed);
        return detail(vid);
    }

    /** A form the engine cannot read is refused before it replaces the current one. */
    private static void readable(byte[] hwp) throws IOException, HwpDocumentException {
        Path probe = Files.createTempFile("yesulin-form-", ".hwp");
        try {
            Files.write(probe, hwp);
            HwpDocument.open(probe).cells();
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    private List<String> problems(Vid vid, int version, List<CellSnapshot> cells) throws IOException {
        List<String> problems = new ArrayList<>();
        if (store.source(vid, version).isEmpty()) {
            problems.add("원본 지원서 파일을 아직 올리지 않았습니다.");
        }
        try {
            FormDefinition definition = builder.definition(vid, version);
            if (!cells.isEmpty()) {
                problems.addAll(SourceCheck.problems(definition, cells));
            }
        } catch (FormExceptions.NotReady invalid) {
            problems.addAll(invalid.problems());
        }
        return problems;
    }

    private int editing(Vid vid) throws IOException {
        List<Integer> versions = store.versions(vid);
        if (versions.isEmpty()) {
            throw new FormExceptions.NotReady(List.of("원본 지원서 파일을 먼저 올려주세요."));
        }
        return versions.getLast();
    }

    private String title(Vid vid, int version) {
        try {
            return version == 0 ? "" : builder.definition(vid, version).title();
        } catch (FormExceptions.NotReady | IOException unusable) {
            return "";
        }
    }

    private static FormViews.Status status(FormStore.State state) {
        if (state.published().isEmpty()) {
            return FormViews.Status.DRAFT;
        }
        return state.closed() ? FormViews.Status.CLOSED : FormViews.Status.PUBLISHED;
    }

    private static FormViews.Cell cell(CellSnapshot cell) {
        return new FormViews.Cell(Cells.format(cell.address()), cell.text(), cell.rowAddress(), cell.columnAddress(),
                cell.rowSpan(), cell.columnSpan());
    }
}
