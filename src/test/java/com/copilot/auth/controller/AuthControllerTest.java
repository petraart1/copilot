package com.copilot.auth.controller;

import com.copilot.auth.dto.request.ChangePasswordRequest;
import com.copilot.auth.dto.request.LoginRequest;
import com.copilot.auth.dto.request.RegisterRequest;
import com.copilot.auth.dto.response.AuthResponse;
import com.copilot.auth.dto.response.LoginResponse;
import com.copilot.auth.dto.response.UserResponse;
import com.copilot.auth.service.AuthService;
import com.copilot.dto.response.RegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-testing-purposes-only-min-32-chars",
        "jwt.issuer=com.copilot",
        "jwt.access-token-ttl=15m",
        "jwt.refresh-token-ttl=7d",
        "spring.redis.host=localhost",
        "spring.redis.port=6379",
        "mailslurp.api-key=test-key",
        "calendar.caldav.base-url=http://localhost:5232",
        "meetings.jitsi.base-url=https://meet.jit.si"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private com.copilot.security.JwtService jwtService;

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "test@example.com",
                "password123",
                "John",
                "Doe"
        );

        UUID userId = UUID.randomUUID();
        RegisterResponse response = new RegisterResponse(
                userId,
                "test@example.com",
                "John",
                "Doe",
                "accessToken",
                "refreshToken"
        );

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.token").value("accessToken"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken"));
    }

    @Test
    void shouldReturnBadRequestWhenRegisterWithInvalidEmail() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "invalid-email",
                "password123",
                "John",
                "Doe"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenRegisterWithShortPassword() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "test@example.com",
                "123",
                "John",
                "Doe"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldLoginUserSuccessfully() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "password123"
        );

        UUID userId = UUID.randomUUID();
        LoginResponse response = new LoginResponse(
                "accessToken",
                "refreshToken",
                new LoginResponse.UserInfo(
                        userId,
                        "test@example.com",
                        "John",
                        "Doe",
                        "EMPLOYEE"
                )
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("accessToken"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken"))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.firstName").value("John"))
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
    }

    @Test
    void shouldReturnBadRequestWhenLoginWithInvalidEmail() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest(
                "invalid-email",
                "password123"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        // Arrange
        AuthResponse response = new AuthResponse(
                "newAccessToken",
                "newRefreshToken"
        );

        when(authService.refresh(anyString())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer refreshToken")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("newAccessToken"))
                .andExpect(jsonPath("$.refreshToken").value("newRefreshToken"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldChangePasswordSuccessfully() throws Exception {
        // Arrange
        ChangePasswordRequest request = new ChangePasswordRequest(
                "oldPassword",
                "newPassword123"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnBadRequestWhenChangePasswordWithShortNewPassword() throws Exception {
        // Arrange
        ChangePasswordRequest request = new ChangePasswordRequest(
                "oldPassword",
                "123"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetCurrentUserSuccessfully() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserResponse response = new UserResponse(
                userId,
                "test@example.com",
                "John",
                "Doe",
                "Sales",
                "EMPLOYEE",
                true
        );

        when(authService.getCurrentUser()).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/auth/me")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.department").value("Sales"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void shouldReturnUnauthorizedWhenGetCurrentUserWithoutAuth() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/auth/me")
                        )
                .andExpect(status().isUnauthorized());
    }
}

