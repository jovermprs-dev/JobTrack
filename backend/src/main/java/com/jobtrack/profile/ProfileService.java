package com.jobtrack.profile;

import com.jobtrack.common.Text;
import com.jobtrack.compatibility.CompatibilityService;
import com.jobtrack.profile.ProfileDtos.LanguageDto;
import com.jobtrack.profile.ProfileDtos.ProfileRequest;
import com.jobtrack.profile.ProfileDtos.ProfileResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final CompatibilityService compatibility;

    public ProfileService(ProfileRepository profiles, CompatibilityService compatibility) {
        this.profiles = profiles;
        this.compatibility = compatibility;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return profiles.findById(userId).map(ProfileResponse::from).orElseGet(ProfileResponse::empty);
    }

    @Transactional
    public ProfileResponse save(UUID userId, ProfileRequest r) {
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        profile.setDegree(Text.clean(r.degree()));
        profile.setExperienceSummary(Text.clean(r.experienceSummary()));
        profile.setYearsExperience(r.yearsExperience());
        profile.setLocation(Text.clean(r.location()));
        profile.setPreferredWorkMode(r.preferredWorkMode());
        profile.setExpectedSalary(r.expectedSalary());
        String currency = Text.clean(r.salaryCurrency());
        profile.setSalaryCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
        profile.setPreferences(Text.clean(r.preferences()));

        profile.getSkills().clear();
        profile.getSkills().addAll(Text.cleanList(r.skills()));
        profile.getDesiredRoles().clear();
        profile.getDesiredRoles().addAll(Text.cleanList(r.desiredRoles()));
        profile.getLanguages().clear();
        if (r.languages() != null) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (LanguageDto l : r.languages()) {
                if (seen.add(l.language().strip().toLowerCase(Locale.ROOT))) {
                    profile.getLanguages().add(new Profile.Language(l.language().strip(), l.level().strip()));
                }
            }
        }
        Profile saved = profiles.saveAndFlush(profile);
        compatibility.recalculateAll(userId);
        return ProfileResponse.from(saved);
    }
}
