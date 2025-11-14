package com.copilot.auth.repository;

import com.copilot.auth.model.ImportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportLogRepository extends JpaRepository<ImportLog, UUID> {
    
    List<ImportLog> findByImportedByUserIdOrderByCreatedAtDesc(UUID userId);
    
    @Query("select il from ImportLog il where il.importedByUserId = :userId order by il.createdAt desc")
    List<ImportLog> findImportHistoryByUserId(@Param("userId") UUID userId);
}

