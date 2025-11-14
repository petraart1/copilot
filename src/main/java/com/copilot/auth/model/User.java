package com.copilot.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "personal_email", length = 255)
    private String personalEmail;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String telegram;

    @Column(length = 50)
    private String department;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String role = "EMPLOYEE";

    @Column(name = "email_provider_id", length = 255)
    private String emailProviderId;

    @Column(name = "email_password", columnDefinition = "TEXT")
    private String emailPassword;

    @Column(name = "calendar_provider_id", length = 255)
    private String calendarProviderId;

    @Column(name = "calendar_password", columnDefinition = "TEXT")
    private String calendarPassword;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_password_changed", nullable = false)
    @Builder.Default
    private Boolean isPasswordChanged = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
