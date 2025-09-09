package com.stocksanalyses.model;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.Instant;

@Entity
@Table(name = "strategy_definitions")
@Audited
public class StrategyDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String strategyId;

    @Column(nullable = false)
    private String version;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String dslJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private String createdBy;

    public Long getId() { return id; }
    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}


