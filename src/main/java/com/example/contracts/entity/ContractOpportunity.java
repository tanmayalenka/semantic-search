package com.example.contracts.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "contract_opportunities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractOpportunity {

    @Id
    @Column(name = "notice_id", nullable = false, updatable = false)
    private String noticeId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "solicitation")
    private String solicitation;

    @Column(name = "department")
    private String department;

    @Column(name = "sub_tier")
    private String subTier;

    @Column(name = "office")
    private String office;

    @Column(name = "type")
    private String type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Stored as pgvector's `vector` type. We expose it to Java as a String
     * literal ("[0.1,0.2,...]") to avoid pulling in a custom Hibernate type
     * or the pgvector Java lib. Reads/writes go through native queries.
     */
    @Column(name = "title_embedding", columnDefinition = "vector(768)")
    private String titleEmbedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}