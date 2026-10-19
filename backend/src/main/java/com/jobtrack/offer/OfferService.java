package com.jobtrack.offer;

import com.jobtrack.common.BadRequestException;
import com.jobtrack.common.NotFoundException;
import com.jobtrack.common.PageResponse;
import com.jobtrack.common.Text;
import com.jobtrack.compatibility.CompatibilityModels.ProfileFacts;
import com.jobtrack.compatibility.CompatibilityModels.Result;
import com.jobtrack.compatibility.CompatibilityService;
import com.jobtrack.offer.OfferDtos.ChangeStatusRequest;
import com.jobtrack.offer.OfferDtos.HistoryEntry;
import com.jobtrack.offer.OfferDtos.OfferRequest;
import com.jobtrack.offer.OfferDtos.OfferResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {

    /** Campos de ordenación permitidos (nombre público → propiedad de la entidad). */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "detectedDate", "detectedDate",
            "company", "company",
            "title", "title",
            "compatibilityScore", "compatibilityScore",
            "updatedAt", "updatedAt");

    private final JobOfferRepository offers;
    private final StatusHistoryRepository history;
    private final CompatibilityService compatibility;
    private final Clock clock;

    public OfferService(JobOfferRepository offers, StatusHistoryRepository history, CompatibilityService compatibility,
            Clock clock) {
        this.offers = offers;
        this.history = history;
        this.compatibility = compatibility;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> list(UUID userId, OfferSpecifications.Filter filter, int page, int size, String sort) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), parseSort(sort));
        return PageResponse.of(offers.findAll(OfferSpecifications.of(userId, filter), pageable), OfferResponse::from);
    }

    @Transactional(readOnly = true)
    public OfferResponse get(UUID userId, UUID id) {
        return OfferResponse.from(find(userId, id));
    }

    @Transactional
    public OfferResponse create(UUID userId, OfferRequest request) {
        return OfferResponse.from(createEntity(userId, request, compatibility.profileFacts(userId)));
    }

    /** Crea y persiste una oferta con su primera entrada de historial. Reutilizado por la importación CSV. */
    @Transactional
    public JobOffer createEntity(UUID userId, OfferRequest request, ProfileFacts profile) {
        JobOffer offer = new JobOffer(userId);
        apply(offer, request);
        OfferStatus status = request.status() == null ? OfferStatus.SAVED : request.status();
        offer.setStatus(status);
        offer.setFavorite(Boolean.TRUE.equals(request.favorite()));
        if (status == OfferStatus.APPLIED && offer.getAppliedDate() == null) {
            offer.setAppliedDate(today());
        }
        compatibility.apply(offer, profile);
        JobOffer saved = offers.save(offer);
        history.save(new StatusHistory(saved.getId(), userId, null, status, clock.instant(), null));
        return saved;
    }

    @Transactional
    public OfferResponse update(UUID userId, UUID id, OfferRequest request) {
        JobOffer offer = find(userId, id);
        apply(offer, request);
        compatibility.apply(offer, compatibility.profileFacts(userId));
        offer.touch();
        return OfferResponse.from(offer);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        offers.delete(find(userId, id));
    }

    @Transactional
    public OfferResponse setFavorite(UUID userId, UUID id, boolean favorite) {
        JobOffer offer = find(userId, id);
        offer.setFavorite(favorite);
        offer.touch();
        return OfferResponse.from(offer);
    }

    @Transactional
    public OfferResponse changeStatus(UUID userId, UUID id, ChangeStatusRequest request) {
        JobOffer offer = find(userId, id);
        OfferStatus previous = offer.getStatus();
        if (previous == request.status()) {
            throw new BadRequestException("La oferta ya se encuentra en ese estado.");
        }
        offer.setStatus(request.status());
        if (request.status() == OfferStatus.APPLIED && offer.getAppliedDate() == null) {
            offer.setAppliedDate(today());
        }
        offer.touch();
        history.save(new StatusHistory(offer.getId(), userId, previous, request.status(), clock.instant(),
                Text.clean(request.note())));
        return OfferResponse.from(offer);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID userId, UUID id) {
        find(userId, id);
        return history.findByOfferIdAndUserIdOrderByChangedAtDescIdDesc(id, userId).stream().map(HistoryEntry::from).toList();
    }

    @Transactional(readOnly = true)
    public Result compatibility(UUID userId, UUID id) {
        return compatibility.explain(find(userId, id), compatibility.profileFacts(userId));
    }

    private JobOffer find(UUID userId, UUID id) {
        return offers.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Oferta no encontrada."));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private void apply(JobOffer o, OfferRequest r) {
        o.setTitle(r.title().strip());
        o.setCompany(r.company().strip());
        o.setDescription(Text.clean(r.description()));
        o.setUrl(Text.clean(r.url()));
        o.setLocation(Text.clean(r.location()));
        o.setWorkMode(r.workMode());
        o.setPublishedDate(r.publishedDate());
        o.setDetectedDate(r.detectedDate() != null ? r.detectedDate()
                : o.getDetectedDate() != null ? o.getDetectedDate() : today());
        o.setAppliedDate(r.appliedDate());
        o.setSalaryMin(r.salaryMin());
        o.setSalaryMax(r.salaryMax());
        String currency = Text.clean(r.currency());
        o.setCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
        o.setContractType(Text.clean(r.contractType()));
        o.setWorkingHours(Text.clean(r.workingHours()));
        o.setMinYearsExperience(r.minYearsExperience());
        o.setRequirements(Text.clean(r.requirements()));
        o.setNotes(Text.clean(r.notes()));
        o.getSkills().clear();
        o.getSkills().addAll(Text.cleanList(r.skills()));
    }

    private static Sort parseSort(String sort) {
        String[] parts = (sort == null || sort.isBlank() ? "detectedDate,desc" : sort).split(",");
        String property = SORT_FIELDS.get(parts[0].strip());
        if (property == null) {
            throw new BadRequestException("Campo de ordenación no permitido: " + parts[0]);
        }
        boolean asc = parts.length > 1 && parts[1].strip().equalsIgnoreCase("asc");
        Sort.Order main = asc ? Sort.Order.asc(property) : Sort.Order.desc(property);
        if (property.equals("company") || property.equals("title")) {
            main = main.ignoreCase();
        }
        if (property.equals("compatibilityScore")) {
            main = main.nullsLast();
        }
        return Sort.by(main, Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }
}
