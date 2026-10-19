package com.jobtrack.demo;

import com.jobtrack.auth.AppUserPrincipal;
import com.jobtrack.common.WorkMode;
import com.jobtrack.compatibility.CompatibilityModels.ProfileFacts;
import com.jobtrack.compatibility.CompatibilityService;
import com.jobtrack.offer.JobOffer;
import com.jobtrack.offer.JobOfferRepository;
import com.jobtrack.offer.OfferStatus;
import com.jobtrack.offer.StatusHistory;
import com.jobtrack.offer.StatusHistoryRepository;
import com.jobtrack.profile.Profile;
import com.jobtrack.profile.ProfileRepository;
import com.jobtrack.user.User;
import com.jobtrack.user.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Solo en modo demo ({@code jobtrack.demo.enabled=true}): al arrancar recrea la cuenta de la demo con un perfil y unas
 * ofertas <strong>ficticias</strong>. Las fechas son relativas a hoy para que el panel y las estadísticas se vean vivos.
 *
 * <p>Se vuelve a sembrar en cada arranque: como la demo es de solo lectura, no hay nada que conservar y así los datos
 * no envejecen. La contraseña de la cuenta es aleatoria y se descarta: nadie puede iniciar sesión con ella (además, en
 * modo demo el login está bloqueado), la cuenta se asigna a todas las peticiones en {@link DemoModeSecurity}.
 */
@Component
@ConditionalOnProperty(name = "jobtrack.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String EMAIL = "demo@jobtrack.local";
    static final String DISPLAY_NAME = "Visitante";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final JobOfferRepository offers;
    private final StatusHistoryRepository history;
    private final CompatibilityService compatibility;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final TransactionTemplate tx;

    private volatile AppUserPrincipal principal;

    public DemoDataSeeder(UserRepository users, ProfileRepository profiles, JobOfferRepository offers,
            StatusHistoryRepository history, CompatibilityService compatibility, PasswordEncoder encoder, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.profiles = profiles;
        this.offers = offers;
        this.history = history;
        this.compatibility = compatibility;
        this.encoder = encoder;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** La cuenta de la demo; vacío hasta que termina el sembrado del arranque. */
    public Optional<AppUserPrincipal> principal() {
        return Optional.ofNullable(principal);
    }

    @Override
    public void run(ApplicationArguments args) {
        this.principal = tx.execute(status -> seed());
        log.info("Modo demo activo: datos de ejemplo sembrados para {} (solo lectura, sin inicio de sesión).", EMAIL);
    }

    private AppUserPrincipal seed() {
        users.findByEmail(EMAIL).ifPresent(old -> {
            users.delete(old); // el esquema borra en cascada perfil, ofertas e historial
            users.flush();     // el DELETE debe ejecutarse antes del INSERT: Hibernate ordena inserciones primero
        });

        User user = users.saveAndFlush(new User(EMAIL, DISPLAY_NAME, encoder.encode(randomSecret())));

        profiles.saveAndFlush(profile(user));
        ProfileFacts facts = compatibility.profileFacts(user.getId());

        LocalDate today = LocalDate.now(clock);
        for (SeedOffer seed : SEED_OFFERS) {
            JobOffer offer = seed.toOffer(user.getId(), today);
            compatibility.apply(offer, facts);
            JobOffer saved = offers.save(offer);
            for (HistoryRow row : seed.historyRows(today, this::at)) {
                history.save(new StatusHistory(saved.getId(), user.getId(), row.from(), row.to(), row.at(), row.note()));
            }
        }
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(), null);
    }

    private static Profile profile(User user) {
        Profile p = new Profile(user.getId());
        p.setDegree("CFGS Desarrollo de Aplicaciones Multiplataforma");
        p.setYearsExperience(new BigDecimal("1.5"));
        p.setExperienceSummary("Desarrollo full stack con Java, Spring Boot y Angular en un proyecto en producción: APIs REST, "
                + "consultas SQL, pruebas unitarias con JUnit y despliegue con Docker. Perfil de ejemplo de la demo.");
        p.setLocation("Alicante");
        p.setPreferredWorkMode(WorkMode.HYBRID);
        p.setExpectedSalary(new BigDecimal("28000"));
        p.setSalaryCurrency("EUR");
        p.setPreferences("Equipo con revisión de código y formación continua. Evito turnos rotativos.");
        p.getSkills().addAll(List.of("Java", "Spring Boot", "Angular", "TypeScript", "SQL", "PostgreSQL", "Docker", "Git", "JUnit"));
        p.getDesiredRoles().addAll(List.of("Desarrollador Full Stack", "Desarrollador Backend Java"));
        p.getLanguages().add(new Profile.Language("Español", "Nativo"));
        p.getLanguages().add(new Profile.Language("Inglés", "B2"));
        return p;
    }

    /** Un instante a media mañana (UTC) del día indicado. */
    private Instant at(LocalDate day) {
        return day.atTime(LocalTime.of(10, 30)).atZone(clock.getZone()).toInstant();
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).substring(0, 40);
    }

    private record Step(int daysAgo, OfferStatus to, String note) {
    }

    private record HistoryRow(OfferStatus from, OfferStatus to, Instant at, String note) {
    }

    private record SeedOffer(String title, String company, String location, WorkMode mode, int minYears, int salaryMin,
            int salaryMax, String contract, List<String> skills, String description, String requirements, String notes,
            boolean favorite, int detectedDaysAgo, List<Step> path) {

        JobOffer toOffer(java.util.UUID userId, LocalDate today) {
            JobOffer o = new JobOffer(userId);
            o.setTitle(title);
            o.setCompany(company);
            o.setDescription(description);
            o.setRequirements(requirements);
            o.setNotes(notes);
            o.setUrl("https://example.com/empleo/" + company.toLowerCase().replace(' ', '-'));
            o.setLocation(location);
            o.setWorkMode(mode);
            o.setDetectedDate(today.minusDays(detectedDaysAgo));
            o.setPublishedDate(today.minusDays(detectedDaysAgo + 2L));
            o.setSalaryMin(BigDecimal.valueOf(salaryMin));
            o.setSalaryMax(BigDecimal.valueOf(salaryMax));
            o.setCurrency("EUR");
            o.setContractType(contract);
            o.setWorkingHours("Jornada completa");
            o.setMinYearsExperience(BigDecimal.valueOf(minYears));
            o.getSkills().addAll(skills);
            o.setFavorite(favorite);
            o.setStatus(path.isEmpty() ? OfferStatus.SAVED : path.get(path.size() - 1).to());
            path.stream().filter(s -> s.to() == OfferStatus.APPLIED).findFirst()
                    .ifPresent(s -> o.setAppliedDate(today.minusDays(s.daysAgo())));
            return o;
        }

        List<HistoryRow> historyRows(LocalDate today, java.util.function.Function<LocalDate, Instant> at) {
            List<HistoryRow> rows = new java.util.ArrayList<>();
            rows.add(new HistoryRow(null, OfferStatus.SAVED, at.apply(today.minusDays(detectedDaysAgo)), null));
            OfferStatus previous = OfferStatus.SAVED;
            for (Step step : path) {
                rows.add(new HistoryRow(previous, step.to(), at.apply(today.minusDays(step.daysAgo())), step.note()));
                previous = step.to();
            }
            return rows;
        }
    }

    private static final List<SeedOffer> SEED_OFFERS = List.of(
            new SeedOffer("Desarrollador/a Full Stack (Java + Angular)", "Nubix Cloud", "Alicante", WorkMode.HYBRID, 1, 28000, 34000,
                    "Indefinido", List.of("Java", "Spring Boot", "Angular", "PostgreSQL", "Docker"),
                    "Producto SaaS para gestión de flotas. Trabajarás en el backend y el panel web con un equipo de seis personas.",
                    "1 año de experiencia con Java y Angular. Valorable Docker.", "Buen ambiente según la entrevista de RR. HH.", true, 40,
                    List.of(new Step(36, OfferStatus.APPLIED, "Candidatura enviada desde el portal de empleo."),
                            new Step(29, OfferStatus.IN_PROCESS, "Llamada con RR. HH."),
                            new Step(15, OfferStatus.INTERVIEW, "Entrevista técnica con el equipo de plataforma."))),
            new SeedOffer("Backend Java Developer", "Brightworks", "Madrid", WorkMode.REMOTE, 3, 38000, 46000,
                    "Indefinido", List.of("Java", "Spring Boot", "Kafka", "AWS", "PostgreSQL"),
                    "Plataforma de pagos con arquitectura orientada a eventos.",
                    "3 años con Java. Experiencia con Kafka y AWS.", null, false, 62,
                    List.of(new Step(58, OfferStatus.APPLIED, null),
                            new Step(50, OfferStatus.IN_PROCESS, "Prueba técnica de Spring Boot."),
                            new Step(41, OfferStatus.REJECTED, "Buscan un perfil con más experiencia en Kafka."))),
            new SeedOffer("Frontend Angular Developer", "Lumen Analytics", "Valencia", WorkMode.HYBRID, 1, 26000, 32000,
                    "Indefinido", List.of("Angular", "TypeScript", "RxJS", "SCSS", "Git"),
                    "Cuadros de mando de analítica para clientes del sector retail.",
                    "Angular y TypeScript. Se valora experiencia con RxJS.", null, true, 21,
                    List.of(new Step(18, OfferStatus.APPLIED, null))),
            new SeedOffer("Ingeniero/a de Software Junior", "Kodiak Labs", "Barcelona", WorkMode.ON_SITE, 0, 27000, 30000,
                    "Indefinido", List.of("Java", "SQL", "Git", "JUnit"),
                    "Programa de incorporación para perfiles junior con mentoría durante seis meses.",
                    "Sin experiencia previa exigida. Java y SQL a nivel de ciclo formativo.", "Pendiente de recibir la oferta por escrito.", true, 75,
                    List.of(new Step(70, OfferStatus.APPLIED, null),
                            new Step(63, OfferStatus.IN_PROCESS, "Prueba de lógica online."),
                            new Step(55, OfferStatus.INTERVIEW, "Entrevista con el responsable del equipo."),
                            new Step(46, OfferStatus.OFFER_RECEIVED, "Oferta verbal: 29.000 € brutos anuales."))),
            new SeedOffer("Desarrollador/a Full Stack TypeScript", "Pixelforge", "Remoto (España)", WorkMode.REMOTE, 2, 32000, 40000,
                    "Indefinido", List.of("TypeScript", "Node.js", "React", "MongoDB"),
                    "Estudio de producto digital. Stack JavaScript de extremo a extremo.",
                    "2 años con TypeScript. Node.js y React.", "Sin Angular ni Java: encaja poco con mi perfil.", false, 5, List.of()),
            new SeedOffer("Programador/a Java", "Atlas Logistics", "Alicante", WorkMode.ON_SITE, 1, 24000, 28000,
                    "Indefinido", List.of("Java", "Spring", "Oracle", "SQL"),
                    "Mantenimiento y evolución de aplicaciones internas de almacén.",
                    "Java y SQL. Valorable Oracle.", null, false, 9,
                    List.of(new Step(8, OfferStatus.PENDING_TO_APPLY, "Preparar la carta de presentación."))),
            new SeedOffer("Desarrollador/a Backend (Spring Boot)", "Mistral Retail", "Alicante", WorkMode.HYBRID, 2, 30000, 36000,
                    "Indefinido", List.of("Java", "Spring Boot", "Docker", "Kubernetes", "PostgreSQL"),
                    "Servicios de catálogo y pedidos para una cadena de tiendas.",
                    "2 años con Spring Boot. Contenedores y Kubernetes.", null, false, 30,
                    List.of(new Step(27, OfferStatus.APPLIED, null),
                            new Step(19, OfferStatus.IN_PROCESS, "Prueba técnica enviada; esperan corrección."))),
            new SeedOffer("Desarrollador/a de Aplicaciones", "Orbita Software", "Murcia", WorkMode.HYBRID, 1, 25000, 30000,
                    "Indefinido", List.of("Java", "Angular", "SQL"),
                    "Aplicaciones de gestión para la administración pública.",
                    "Java, Angular y SQL.", null, false, 14,
                    List.of(new Step(12, OfferStatus.APPLIED, null))),
            new SeedOffer("Analista Programador/a", "Cobalt Systems", "Valencia", WorkMode.ON_SITE, 2, 26000, 31000,
                    "Indefinido", List.of("Java", "Oracle", "PL/SQL", "Jenkins"),
                    "Sistemas de facturación para el sector financiero.",
                    "2 años con Java y Oracle. PL/SQL.", null, false, 50,
                    List.of(new Step(47, OfferStatus.APPLIED, null),
                            new Step(38, OfferStatus.WITHDRAWN, "Retirada: el puesto exige desplazamiento diario."))),
            new SeedOffer("Full Stack Developer (Angular + Java)", "Verde Digital", "Remoto (España)", WorkMode.REMOTE, 1, 29000, 35000,
                    "Indefinido", List.of("Angular", "Java", "Spring Boot", "Docker", "Git"),
                    "Plataforma de gestión energética para pymes.",
                    "1 año con Angular y Java. Docker.", null, false, 6,
                    List.of(new Step(4, OfferStatus.APPLIED, null))),
            new SeedOffer("Desarrollador/a Web", "Tramontana", "Alicante", WorkMode.ON_SITE, 0, 20000, 23000,
                    "Temporal", List.of("HTML", "CSS", "JavaScript", "PHP"),
                    "Webs corporativas y tiendas online para clientes locales.",
                    "HTML, CSS y JavaScript. PHP.", null, false, 85,
                    List.of(new Step(82, OfferStatus.APPLIED, null),
                            new Step(70, OfferStatus.REJECTED, "Proceso cerrado sin feedback."))),
            new SeedOffer("Software Engineer, Platform", "Helix Health", "Remoto (UE)", WorkMode.REMOTE, 4, 45000, 55000,
                    "Indefinido", List.of("Java", "Kotlin", "Kubernetes", "Terraform", "AWS"),
                    "Equipo de plataforma de una empresa de salud digital.",
                    "4 años de experiencia. Kubernetes y Terraform.", "Por encima de mi experiencia actual.", false, 3, List.of()));
}
