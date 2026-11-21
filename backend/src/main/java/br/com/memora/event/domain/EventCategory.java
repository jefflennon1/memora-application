package br.com.memora.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Categoria de evento (dado de referência, mantido por migrations Flyway).
 */
@Entity
@Table(name = "event_category")
public class EventCategory {

    @Id
    @Column(name = "code", length = 40, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected EventCategory() {
        // JPA
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
