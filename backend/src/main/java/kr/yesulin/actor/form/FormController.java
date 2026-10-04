package kr.yesulin.actor.form;

import java.io.IOException;
import kr.yesulin.actor.document.HwpDocumentException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** The applicant's side of a notice link: the published form and building their own file from it. */
@RestController
@RequestMapping("/api/forms")
public final class FormController {
    private final FormApplyService forms;

    public FormController(FormApplyService forms) {
        this.forms = forms;
    }

    @GetMapping("/{vid}")
    public FormViews.PublicForm form(@PathVariable String vid) throws IOException {
        return forms.form(new Vid(vid));
    }

    @PostMapping(path = "/{vid}/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/x-hwp")
    public ResponseEntity<ByteArrayResource> generate(
            @PathVariable String vid,
            @RequestPart("request") FormViews.GenerateRequest request,
            @RequestHeader(value = kr.yesulin.actor.document.DocumentEdits.TOKEN_HEADER, required = false) String editToken,
            MultipartHttpServletRequest multipart) throws IOException, HwpDocumentException {
        return FormRequests.built(forms.generate(new Vid(vid), request, FormRequests.photos(multipart), editToken));
    }
}
