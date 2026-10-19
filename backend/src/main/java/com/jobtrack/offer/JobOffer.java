package com.jobtrack.offer;

import com.jobtrack.common.WorkMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_offers")
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 2000)
    private String url;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 16)
    private WorkMode workMode;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(name = "detected_date", nullable = false)
    private LocalDate detectedDate;

    @Column(name = "applied_date")
    private LocalDate appliedDate;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(length = 3)
    private String currency;

    @Column(name = "contract_type", length = 80)
    private String contractType;

    @Column(name = "working_hours", length = 80)
    private String workingHours;

    @Column(name = "min_years_experience", precision = 4, scale = 1)
    private BigDecimal minYearsExperience;

    @Column(columnDefinition = "text")
    private String requirements;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OfferStatus status = OfferStatus.SAVED;

    @Column(nullable = false)
    private boolean favorite;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "compatibility_score")
    private Integer compatibilityScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "job_offer_skills", joinColumns = @JoinColumn(name = "offer_id"))
    @Column(name = "name", length = 80)
    private Set<String> skills = new LinkedHashSet<>();

    protected JobOffer() {
    }

    public JobOffer(UUID userId) {
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    /** Marca una modificación hecha por la persona usuaria (el recálculo automático de compatibilidad no la marca). */
    public void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
    public LocalDate getPublishedDate() { return publishedDate; }
    public void setPublishedDate(LocalDate publishedDate) { this.publishedDate = publishedDate; }
    public LocalDate getDetectedDate() { return detectedDate; }
    public void setDetectedDate(LocalDate detectedDate) { this.detectedDate = detectedDate; }
    public LocalDate getAppliedDate() { return appliedDate; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
    public BigDecimal getSalaryMin() { return salaryMin; }
    public void setSalaryMin(BigDecimal salaryMin) { this.salaryMin = salaryMin; }
    public BigDecimal getSalaryMax() { return salaryMax; }
    public void setSalaryMax(BigDecimal salaryMax) { this.salaryMax = salaryMax; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }
    public String getWorkingHours() { return workingHours; }
    public void setWorkingHours(String workingHours) { this.workingHours = workingHours; }
    public BigDecimal getMinYearsExperience() { return minYearsExperience; }
    public void setMinYearsExperience(BigDecimal minYearsExperience) { this.minYearsExperience = minYearsExperience; }
    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus status) { this.status = status; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public Integer getCompatibilityScore() { return compatibilityScore; }
    public void setCompatibilityScore(Integer compatibilityScore) { this.compatibilityScore = compatibilityScore; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<String> getSkills() { return skills; }
}
