package br.com.memora.identity.domain;

import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Conta de um provedor de identidade (ex.: Google) vinculada a uma conta Memora.
 *
 * <p>É reconhecida exclusivamente por {@code (provider, providerSubject)}: o {@code sub} do OpenID Connect
 * é estável, enquanto o e-mail pode mudar e é apenas informativo.
 */
@Entity
@Table(name = "external_identity")
public class ExternalIdentity extends BaseEntity {

    public static final int EMAIL_MAX = 320;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false, updatable = false)
    private IdentityProvider provider;

    @Column(name = "provider_subject", length = 255, nullable = false, updatable = false)
    private String providerSubject;

    @Column(name = "email", length = EMAIL_MAX)
    private String email;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected ExternalIdentity() {
        // JPA
    }

    public static ExternalIdentity link(User user, IdentityProvider provider, String subject,
                                        String email, Boolean emailVerified, Instant now) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.user = Objects.requireNonNull(user);
        identity.provider = Objects.requireNonNull(provider);
        identity.providerSubject = Objects.requireNonNull(subject);
        identity.email = sanitizeEmail(email);
        identity.emailVerified = emailVerified;
        identity.linkedAt = now;
        identity.lastLoginAt = now;
        return identity;
    }

    /** Registra um novo login, atualizando os dados informativos enviados pelo provedor. */
    public void recordLogin(String email, Boolean emailVerified, Instant now) {
        this.email = sanitizeEmail(email);
        this.emailVerified = emailVerified;
        this.lastLoginAt = now;
    }

    private static String sanitizeEmail(String email) {
        return email == null || email.isBlank() || email.length() > EMAIL_MAX ? null : email.strip();
    }

    public User getUser() {
        return user;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getEmail() {
        return email;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
