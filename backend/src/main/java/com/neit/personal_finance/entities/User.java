package com.neit.personal_finance.entities;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator)
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Builder.Default
    private LocalDateTime createDt = LocalDateTime.now();

    private String role;

    @JsonIgnore
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long cacheRevision;
}
