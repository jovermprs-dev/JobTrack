package com.jobtrack.profile;

import com.jobtrack.common.WorkMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

    @Embeddable
    public record Language(@Column(name = "language", length = 60) String language,
            @Column(name = "level", length = 40) String level) {
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 200)
    private String degree;

    @Column(name = "experience_summary", columnDefinition = "text")
    private String experienceSummary;

    @Column(name = "years_experience", precision = 4, scale = 1)
    private BigDecimal yearsExperience;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_work_mode", length = 16)
    private WorkMode preferredWorkMode;

    @Column(name = "expected_salary", precision = 12, scale = 2)
    private BigDecimal expectedSalary;

    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Column(columnDefinition = "text")
    private String preferences;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "profile_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "name", length = 80)
    private Set<String> skills = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "profile_languages", joinColumns = @JoinColumn(name = "user_id"))
    private Set<Language> languages = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "profile_desired_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 120)
    private Set<String> desiredRoles = new LinkedHashSet<>();

    protected Profile() {
    }

    public Profile(UUID userId) {
        this.userId = userId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }
    public String getExperienceSummary() { return experienceSummary; }
    public void setExperienceSummary(String experienceSummary) { this.experienceSummary = experienceSummary; }
    public BigDecimal getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(BigDecimal yearsExperience) { this.yearsExperience = yearsExperience; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public WorkMode getPreferredWorkMode() { return preferredWorkMode; }
    public void setPreferredWorkMode(WorkMode preferredWorkMode) { this.preferredWorkMode = preferredWorkMode; }
    public BigDecimal getExpectedSalary() { return expectedSalary; }
    public void setExpectedSalary(BigDecimal expectedSalary) { this.expectedSalary = expectedSalary; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }
    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<String> getSkills() { return skills; }
    public Set<Language> getLanguages() { return languages; }
    public Set<String> getDesiredRoles() { return desiredRoles; }
}
