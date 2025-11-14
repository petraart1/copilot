package com.copilot.auth.repository;

import com.copilot.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    List<User> findAllByIsActiveTrueAndDeletedAtIsNull();
    
    @Query("select u from User u where u.createdAt >= :since and u.deletedAt is null")
    List<User> findRecentUsers(@Param("since") LocalDateTime since);
    
    long countByIsActiveTrueAndDeletedAtIsNull();
    
    Optional<User> findByPersonalEmail(String personalEmail);
    
    List<User> findAllByPersonalEmailIsNotNull();
    
    List<User> findAllByEmailProviderIdIsNotNull();
}
