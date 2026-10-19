package com.jobtrack.offer;

import com.jobtrack.common.Text;
import com.jobtrack.common.WorkMode;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class OfferSpecifications {

    private OfferSpecifications() {
    }

    public record Filter(String q, List<OfferStatus> statuses, String company, String location, WorkMode workMode,
            Boolean favorite) {
    }

    /** Siempre restringe por propietario; los demás filtros son opcionales y combinables (AND). */
    public static Specification<JobOffer> of(UUID userId, Filter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));

            String q = Text.clean(f.q());
            if (q != null) {
                String pattern = like(q);
                Subquery<Integer> skillMatch = query.subquery(Integer.class);
                Root<JobOffer> correlated = skillMatch.correlate(root);
                Join<JobOffer, String> skill = correlated.join("skills");
                skillMatch.select(cb.literal(1)).where(cb.like(cb.lower(skill), pattern, '\\'));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("company")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern, '\\'),
                        cb.exists(skillMatch)));
            }
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                predicates.add(root.get("status").in(f.statuses()));
            }
            String company = Text.clean(f.company());
            if (company != null) {
                predicates.add(cb.like(cb.lower(root.get("company")), like(company), '\\'));
            }
            String location = Text.clean(f.location());
            if (location != null) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.<String>get("location"), "")), like(location), '\\'));
            }
            if (f.workMode() != null) {
                predicates.add(cb.equal(root.get("workMode"), f.workMode()));
            }
            if (f.favorite() != null) {
                predicates.add(cb.equal(root.get("favorite"), f.favorite()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String like(String text) {
        return "%" + Text.escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
    }
}
