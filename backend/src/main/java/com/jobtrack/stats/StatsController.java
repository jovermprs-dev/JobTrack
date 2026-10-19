package com.jobtrack.stats;

import com.jobtrack.auth.AppUserPrincipal;
import com.jobtrack.stats.StatsDtos.StatsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping
    public StatsResponse stats(@AuthenticationPrincipal AppUserPrincipal user) {
        return service.compute(user.getId());
    }
}
