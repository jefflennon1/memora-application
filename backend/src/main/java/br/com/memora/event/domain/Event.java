package br.com.memora.event.domain;

import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.persistence.BaseEntity;
import br.com.memora.shared.text.SearchText;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento: o acontecimento que reúne as memórias.
 *
 * <p>Invariantes: nome não vazio; agenda coerente ({@link EventSchedule}); cidade e endereço apenas em
 * eventos presenciais ({@link EventLocation}); organizador principal permanente. O mural nasce aberto e só
 * muda por decisão explícita.
 */
@Entity
@Table(name = "event")
public class Event extends BaseEntity {

    public static final int NAME_MAX = 120;
    public static final int DESCRIPTION_MAX = 2000;

    @Column(name = "name", length = NAME_MAX, nullable = false)
    private String name;

    @Column(name = "name_search", length = NAME_MAX, nullable = false)
    private String nameSearch;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    @Column(name = "category_code", length = 40)
    private String categoryCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", length = 20, nullable = false)
    private EventModality modality;

    @Column(name = "city", length = EventLocation.CITY_MAX)
    private String city;

    @Column(name = "city_search", length = EventLocation.CITY_MAX)
    private String citySearch;

    @Column(name = "address", length = EventLocation.ADDRESS_MAX)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy", length = 20, nullable = false)
    private EventPrivacy privacy;

    @Enumerated(EnumType.STRING)
    @Column(name = "mural_status", length = 20, nullable = false)
    private MuralStatus muralStatus;

    @Column(name = "mural_status_changed_at")
    private Instant muralStatusChangedAt;

    @Column(name = "organizer_id", nullable = false, updatable = false)
    private UUID organizerId;

    @Column(name = "cover_storage_key", length = 200)
    private String coverStorageKey;

    @Column(name = "cover_content_type", length = 50)
    private String coverContentType;

    @Column(name = "cover_size_bytes")
    private Long coverSizeBytes;

    @Column(name = "cover_width")
    private Integer coverWidth;

    @Column(name = "cover_height")
    private Integer coverHeight;

    @Column(name = "cover_updated_at")
    private Instant coverUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Event() {
        // JPA
    }

    /**
     * Cria um evento com o mural aberto.
     *
     * @param earliestAllowedStart primeiro dia aceito como início (ver {@link EventSchedule#requireNotInPast})
     */
    public static Event create(UUID organizerId, EventDetails details, EventPrivacy privacy,
                               LocalDate earliestAllowedStart, Instant now) {
        details.schedule().requireNotInPast(earliestAllowedStart);
        Event event = new Event();
        event.organizerId = Objects.requireNonNull(organizerId);
        event.apply(details);
        event.privacy = Objects.requireNonNull(privacy);
        event.muralStatus = MuralStatus.OPEN;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    /**
     * Substitui os dados descritivos. A regra "início não pode estar no passado" só vale quando a data de
     * início muda: um evento que já aconteceu continua editável (descrição, local…).
     */
    public void update(EventDetails details, LocalDate earliestAllowedStart) {
        if (!details.schedule().startDate().equals(startDate)) {
            details.schedule().requireNotInPast(earliestAllowedStart);
        }
        apply(details);
    }

    public void changePrivacy(EventPrivacy privacy) {
        this.privacy = Objects.requireNonNull(privacy);
    }

    /** Abre ou fecha o mural. Pedir o estado atual não altera nada. */
    public void changeMuralStatus(MuralStatus status, Instant now) {
        if (Objects.requireNonNull(status) != muralStatus) {
            this.muralStatus = status;
            this.muralStatusChangedAt = now;
        }
    }

    public void replaceCover(String storageKey, String contentType, long sizeBytes, int width, int height,
                             Instant now) {
        this.coverStorageKey = Objects.requireNonNull(storageKey);
        this.coverContentType = Objects.requireNonNull(contentType);
        this.coverSizeBytes = sizeBytes;
        this.coverWidth = width;
        this.coverHeight = height;
        this.coverUpdatedAt = Objects.requireNonNull(now);
    }

    public void removeCover() {
        this.coverStorageKey = null;
        this.coverContentType = null;
        this.coverSizeBytes = null;
        this.coverWidth = null;
        this.coverHeight = null;
        this.coverUpdatedAt = null;
    }

    /** Dados atuais, prontos para receber alterações parciais. */
    public EventDetails details() {
        return new EventDetails(name, description, categoryCode,
                new EventSchedule(startDate, startTime, endDate, endTime),
                new EventLocation(modality, city, address));
    }

    private void apply(EventDetails details) {
        this.name = details.name();
        this.nameSearch = SearchText.normalize(details.name());
        this.description = details.description();
        this.categoryCode = details.categoryCode();
        this.startDate = details.schedule().startDate();
        this.startTime = details.schedule().startTime();
        this.endDate = details.schedule().endDate();
        this.endTime = details.schedule().endTime();
        this.modality = details.location().modality();
        this.city = details.location().city();
        this.citySearch = SearchText.normalize(details.location().city());
        this.address = details.location().address();
    }

    @PreUpdate
    void touch() {
        // Precisão do banco (microssegundos).
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isPrivate() {
        return privacy == EventPrivacy.PRIVATE;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public EventModality getModality() {
        return modality;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public EventPrivacy getPrivacy() {
        return privacy;
    }

    public MuralStatus getMuralStatus() {
        return muralStatus;
    }

    public Instant getMuralStatusChangedAt() {
        return muralStatusChangedAt;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public String getCoverStorageKey() {
        return coverStorageKey;
    }

    public String getCoverContentType() {
        return coverContentType;
    }

    public Instant getCoverUpdatedAt() {
        return coverUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Dados descritivos de um evento, já validados: nome (1 a {@value #NAME_MAX} caracteres, sem espaços nas
     * pontas), descrição opcional, categoria opcional, agenda e local.
     */
    public record EventDetails(String name, String description, String categoryCode, EventSchedule schedule,
                               EventLocation location) {

        public EventDetails {
            name = name == null ? "" : name.strip();
            if (name.isEmpty() || name.length() > NAME_MAX) {
                throw new ValidationException("INVALID_EVENT_NAME",
                        "Informe um nome com 1 a " + NAME_MAX + " caracteres.");
            }
            description = description == null || description.isBlank() ? null : description.strip();
            if (description != null && description.length() > DESCRIPTION_MAX) {
                throw new ValidationException("INVALID_EVENT_DESCRIPTION",
                        "A descrição aceita no máximo " + DESCRIPTION_MAX + " caracteres.");
            }
            categoryCode = categoryCode == null || categoryCode.isBlank() ? null : categoryCode.strip();
            Objects.requireNonNull(schedule);
            Objects.requireNonNull(location);
        }
    }
}
