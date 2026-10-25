package com.neit.personal_finance.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_idempotency_user_key", columnNames = {"user_id", "key_hash"}))
@Getter @Setter @NoArgsConstructor
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;
    public long getUserId() { return user.getId(); }
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String responseBody;
    @Column(nullable = false)
    private Instant createdAt;
}
