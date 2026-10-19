package com.jobtrack.profile;

import com.jobtrack.auth.AppUserPrincipal;
import com.jobtrack.profile.ProfileDtos.ProfileRequest;
import com.jobtrack.profile.ProfileDtos.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.get(principal.getId());
    }

    @PutMapping
    public ProfileResponse save(@AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody ProfileRequest request) {
        return service.save(principal.getId(), request);
    }
}
