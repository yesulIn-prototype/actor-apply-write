package kr.yesulin.actor.form;

import java.io.IOException;
import java.util.Map;
import kr.yesulin.actor.document.HwpDocumentException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * What an applicant reaches through a notice link: only published versions, nothing once the link is
 * closed. A screen keeps the version it opened with, so a fix published meanwhile never mixes into it.
 */
@Service
public final class FormApplyService {
    private final FormStore store;
    private final FormBuilder builder;

    public FormApplyService(FormStore store, FormBuilder builder) {
        this.store = store;
        this.builder = builder;
    }

    public FormViews.PublicForm form(Vid vid) throws IOException {
        FormStore.State state = open(vid);
        int version = state.current().orElseThrow(FormExceptions.NotFound::new);
        FormDefinition definition = builder.definition(vid, version);
        return new FormViews.PublicForm(vid.value(), version, definition.title(), definition.fileName(),
                store.info(vid, version).originalName(), definition.items(), definition.submission(),
                store.spec(vid, version).isPresent());
    }

    public FormBuilder.Built generate(Vid vid, FormViews.GenerateRequest request, Map<String, MultipartFile> photos)
            throws IOException, HwpDocumentException {
        FormStore.State state = open(vid);
        if (!state.published().contains(request.version())) {
            throw new FormExceptions.Changed();
        }
        return builder.build(vid, request.version(), owner(vid, request.version()), request, photos, true);
    }

    static String owner(Vid vid, int version) {
        return "form:" + vid.value() + ":v" + version;
    }

    private FormStore.State open(Vid vid) throws IOException {
        FormStore.State state = store.state(vid);
        if (state.published().isEmpty()) {
            throw new FormExceptions.NotFound();
        }
        if (state.closed()) {
            throw new FormExceptions.Closed();
        }
        return state;
    }
}
