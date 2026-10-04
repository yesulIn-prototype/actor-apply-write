package kr.yesulin.actor.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class UsageController {
    private final UsageCounter usage;
    public UsageController(UsageCounter usage) { this.usage = usage; }

    @GetMapping("/api/admin/usage")
    public UsageCounter.Snapshot snapshot() { return usage.snapshot(); }
}
