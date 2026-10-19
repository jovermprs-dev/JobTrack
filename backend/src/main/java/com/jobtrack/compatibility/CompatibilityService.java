package com.jobtrack.compatibility;

import com.jobtrack.compatibility.CompatibilityModels.OfferFacts;
import com.jobtrack.compatibility.CompatibilityModels.ProfileFacts;
import com.jobtrack.compatibility.CompatibilityModels.Result;
import com.jobtrack.offer.JobOffer;
import com.jobtrack.offer.JobOfferRepository;
import com.jobtrack.profile.ProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompatibilityService {

    private final CompatibilityCalculator calculator;
    private final ProfileRepository profiles;
    private final JobOfferRepository offers;

    public CompatibilityService(CompatibilityCalculator calculator, ProfileRepository profiles, JobOfferRepository offers) {
        this.calculator = calculator;
        this.profiles = profiles;
        this.offers = offers;
    }

    @Transactional(readOnly = true)
    public ProfileFacts profileFacts(UUID userId) {
        return profiles.findById(userId)
                .map(p -> new ProfileFacts(p.getSkills(), p.getYearsExperience(), p.getDesiredRoles(),
                        p.getPreferredWorkMode(), p.getLocation()))
                .orElseGet(ProfileFacts::empty);
    }

    public Result explain(JobOffer offer, ProfileFacts profile) {
        return calculator.calculate(factsOf(offer), profile);
    }

    /** Recalcula y asigna la puntuación persistida de una oferta (dentro de la transacción del llamante). */
    public void apply(JobOffer offer, ProfileFacts profile) {
        offer.setCompatibilityScore(explain(offer, profile).score());
    }

    /** Recalcula todas las ofertas del usuario; se invoca al guardar el perfil. */
    @Transactional
    public void recalculateAll(UUID userId) {
        ProfileFacts facts = profileFacts(userId);
        for (JobOffer offer : offers.findAllByUserIdOrderByDetectedDateDescCreatedAtDesc(userId)) {
            apply(offer, facts);
        }
    }

    private static OfferFacts factsOf(JobOffer o) {
        return new OfferFacts(o.getTitle(), o.getSkills(), o.getMinYearsExperience(), o.getWorkMode(), o.getLocation());
    }
}
