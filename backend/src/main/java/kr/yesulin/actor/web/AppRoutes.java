package kr.yesulin.actor.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Addresses the single-page app answers itself: a notice link (/apply/22382) and the operator page.
 * The server hands them the app's page; the app reads the address.
 */
@Controller
public final class AppRoutes {
    @GetMapping({"/apply/{vid:[0-9]{1,12}}", "/admin", "/admin/", "/admin/forms/{vid:[0-9]{1,12}}"})
    public String app() {
        return "forward:/index.html";
    }
}
