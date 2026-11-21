package br.com.memora.identity.domain;

import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** Hash da senha de uma conta criada diretamente na Memora. */
@Entity
@Table(name = "local_credential")
public class LocalCredential extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false, unique = true)
    private User user;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    protected LocalCredential() {
        // JPA
    }

    public static LocalCredential create(User user, String passwordHash, Instant now) {
        LocalCredential credential = new LocalCredential();
        credential.user = Objects.requireNonNull(user);
        credential.passwordHash = Objects.requireNonNull(passwordHash);
        credential.createdAt = Objects.requireNonNull(now);
        credential.passwordChangedAt = now;
        return credential;
    }

    public User getUser() {
        return user;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
