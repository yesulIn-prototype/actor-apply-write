package kr.yesulin.actor.form;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The standard form's settings menu for the operator's screen (behind the admin token, like all of /api/admin). */
@RestController
@RequestMapping("/api/admin/standard")
public final class AdminStandardController {
    private final StandardForms standards;

    public AdminStandardController(StandardForms standards) {
        this.standards = standards;
    }

    @GetMapping
    public FormViews.StandardCatalog catalog() {
        return standards.catalog();
    }
}
