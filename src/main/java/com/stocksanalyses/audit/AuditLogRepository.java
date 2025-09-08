package com.stocksanalyses.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditLog> findByTimestampBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditLog> findByUserIdAndTimestampBetween(@Param("userId") String userId, 
                                                   @Param("startTime") Instant startTime, 
                                                   @Param("endTime") Instant endTime);
}
