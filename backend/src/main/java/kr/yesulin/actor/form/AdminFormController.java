package kr.yesulin.actor.form;

import java.io.IOException;
import kr.yesulin.actor.document.HwpDocumentException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** The operator's form tools. Every path here sits behind the admin token (see AdminTokenFilter). */
@RestController
@RequestMapping("/api/admin/forms")
public final class AdminFormController {
    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");
    private final FormAdminService forms;

    public AdminFormController(FormAdminService forms) {
        this.forms = forms;
    }

    @GetMapping
    public FormViews.SummaryPage list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "false") boolean deleted) throws IOException {
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지는 1 이상이어야 합니다.");
        }
        return forms.list(query, page, deleted);
    }

    @DeleteMapping("/{vid}")
    public ResponseEntity<Void> delete(@PathVariable String vid) throws IOException {
        forms.delete(new Vid(vid));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{vid}/restore")
    public FormViews.Detail restore(@PathVariable String vid) throws IOException, HwpDocumentException {
        return forms.restore(new Vid(vid));
    }

    @GetMapping("/{vid}")
    public FormViews.Detail detail(@PathVariable String vid) throws IOException, HwpDocumentException {
        return forms.detail(new Vid(vid));
    }

    @PostMapping(path = "/{vid}/source", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FormViews.Detail source(@PathVariable String vid, @RequestPart("document") MultipartFile document)
            throws IOException, HwpDocumentException {
        return forms.uploadSource(new Vid(vid), document);
    }

    @PutMapping("/{vid}/definition")
    public FormViews.Detail definition(@PathVariable String vid, @RequestBody String definition)
            throws IOException, HwpDocumentException {
        return forms.saveDefinition(new Vid(vid), definition);
    }

    @GetMapping("/{vid}/layout")
    public FormViews.Layout layout(@PathVariable String vid) throws IOException, HwpDocumentException {
        return forms.layout(new Vid(vid));
    }

    @GetMapping("/{vid}/layout/{page}")
    public ResponseEntity<ByteArrayResource> layoutPage(@PathVariable String vid, @PathVariable int page)
            throws IOException, HwpDocumentException {
        byte[] svg = forms.layoutPage(new Vid(vid), page);
        return ResponseEntity.ok().contentType(SVG).contentLength(svg.length).body(new ByteArrayResource(svg));
    }

    @GetMapping("/{vid}/form")
    public FormViews.PublicForm form(@PathVariable String vid) throws IOException {
        return forms.editingForm(new Vid(vid));
    }

    @PostMapping(path = "/{vid}/test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/x-hwp")
    public ResponseEntity<ByteArrayResource> test(
            @PathVariable String vid,
            @RequestPart("request") FormViews.GenerateRequest request,
            MultipartHttpServletRequest multipart) throws IOException, HwpDocumentException {
        return FormRequests.built(forms.test(new Vid(vid), request, FormRequests.photos(multipart)));
    }

    @PostMapping("/{vid}/publish")
    public FormViews.Detail publish(@PathVariable String vid) throws IOException, HwpDocumentException {
        return forms.publish(new Vid(vid));
    }

    @PostMapping("/{vid}/close")
    public FormViews.Detail close(@PathVariable String vid) throws IOException, HwpDocumentException {
        return forms.close(new Vid(vid), true);
    }

    @PostMapping("/{vid}/reopen")
    public FormViews.Detail reopen(@PathVariable String vid) throws IOException, HwpDocumentException {
        return forms.close(new Vid(vid), false);
    }
}
