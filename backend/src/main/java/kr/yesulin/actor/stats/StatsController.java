package kr.yesulin.actor.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class StatsController {
    private final CompletionCounter counter;

    public StatsController(CompletionCounter counter) {
        this.counter = counter;
    }

    @GetMapping("/api/stats")
    public StatsResponse stats() {
        return new StatsResponse(counter.count());
    }

    public record StatsResponse(long completedCount) {}
}
