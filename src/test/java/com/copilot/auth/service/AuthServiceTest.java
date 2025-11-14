package com.copilot.auth.service;

import com.copilot.auth.dto.request.ChangePasswordRequest;
import com.copilot.auth.dto.request.LoginRequest;
import com.copilot.auth.dto.request.RegisterRequest;
import com.copilot.auth.dto.response.AuthResponse;
import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.dto.response.RegisterResponse;
import com.copilot.exception.UserAlreadyExistsException;
import com.copilot.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("$2a$10$encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .role("EMPLOYEE")
                .isActive(true)
                .isPasswordChanged(false)
                .createdAt(LocalDateTime.now())
                .build();

        registerRequest = new RegisterRequest(
                "test@example.com",
                "password123",
                "John",
                "Doe"
        );

        loginRequest = new LoginRequest(
                "test@example.com",
                "password123"
        );
    }

    @Test
    void shouldRegisterUserWhenValidDataProvided() {
        // Arrange
        when(repository.existsByEmail("test@example.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("$2a$10$encodedPassword");
        when(repository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(anyString(), anyString(), anyList()))
                .thenReturn("accessToken");
        when(jwtService.generateRefreshToken(anyString(), anyString()))
                .thenReturn("refreshToken");

        // Act
        RegisterResponse response = authService.register(registerRequest);

        // Assert
        assertNotNull(response);
        assertEquals(testUser.getId(), response.id());
        assertEquals("test@example.com", response.email());
        assertEquals("John", response.firstName());
        assertEquals("Doe", response.lastName());
        assertEquals("accessToken", response.token());
        assertEquals("refreshToken", response.refreshToken());

        verify(repository, times(1)).existsByEmail("test@example.com");
        verify(repository, times(1)).save(any(User.class));
        verify(encoder, times(1)).encode("password123");
        verify(jwtService, times(1)).generateAccessToken(anyString(), anyString(), anyList());
        verify(jwtService, times(1)).generateRefreshToken(anyString(), anyString());
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Arrange
        when(repository.existsByEmail("test@example.com")).thenReturn(true);

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class, () -> authService.register(registerRequest));

        verify(repository, times(1)).existsByEmail("test@example.com");
        verify(repository, never()).save(any(User.class));
        verify(jwtService, never()).generateAccessToken(anyString(), anyString(), anyList());
    }

    @Test
    void shouldLoginUserWhenValidCredentials() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(repository.findByEmailAndDeletedAtIsNull("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(repository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(anyString(), anyString(), anyList()))
                .thenReturn("accessToken");
        when(jwtService.generateRefreshToken(anyString(), anyString()))
                .thenReturn("refreshToken");

        // Act
        var response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("accessToken", response.token());
        assertEquals("refreshToken", response.refreshToken());
        assertNotNull(response.user());
        assertEquals(testUser.getId(), response.user().id());
        assertEquals("test@example.com", response.user().email());

        verify(authManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(repository, times(1)).findByEmailAndDeletedAtIsNull("test@example.com");
        verify(repository, times(1)).save(any(User.class));
        verify(jwtService, times(1)).generateAccessToken(anyString(), anyString(), anyList());
        verify(jwtService, times(1)).generateRefreshToken(anyString(), anyString());
    }

    @Test
    void shouldThrowExceptionWhenLoginWithInvalidCredentials() {
        // Arrange
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

        verify(authManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenUserIsInactive() {
        // Arrange
        testUser.setIsActive(false);
        Authentication authentication = mock(Authentication.class);
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(repository.findByEmailAndDeletedAtIsNull("test@example.com"))
                .thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

        verify(authManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(repository, times(1)).findByEmailAndDeletedAtIsNull("test@example.com");
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void shouldChangePasswordWhenValidOldPassword() {
        // Arrange
        String email = "test@example.com";
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "oldPassword",
                "newPassword123"
        );

        when(repository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.of(testUser));
        when(encoder.matches("oldPassword", testUser.getPassword())).thenReturn(true);
        when(encoder.encode("newPassword123")).thenReturn("$2a$10$newEncodedPassword");
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user; // Возвращаем тот же объект, который был передан
        });

        // Act
        assertDoesNotThrow(() -> authService.changePassword(request));

        // Assert
        verify(repository, atLeastOnce()).findByEmailAndDeletedAtIsNull(email);
        verify(encoder, times(1)).matches("oldPassword", testUser.getPassword());
        verify(encoder, times(1)).encode("newPassword123");
        verify(repository, times(1)).save(any(User.class));

        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldThrowExceptionWhenOldPasswordIsIncorrect() {
        // Arrange
        String email = "test@example.com";
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "wrongPassword",
                "newPassword123"
        );

        when(repository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.of(testUser));
        when(encoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.changePassword(request));

        verify(repository, times(1)).findByEmailAndDeletedAtIsNull(email);
        verify(encoder, times(1)).matches("wrongPassword", testUser.getPassword());
        verify(encoder, never()).encode(anyString());
        verify(repository, never()).save(any(User.class));

        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRefreshTokenSuccessfully() {
        // Arrange
        String refreshToken = "validRefreshToken";
        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = mock(io.jsonwebtoken.Jws.class);
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);

        when(jwtService.parse(refreshToken)).thenReturn(jws);
        when(jws.getPayload()).thenReturn(claims);
        when(claims.getSubject()).thenReturn("test@example.com");
        when(claims.get("type")).thenReturn("refresh");
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(repository.findByEmailAndDeletedAtIsNull("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(eq("test@example.com"), eq(testUser.getId().toString()), anyList()))
                .thenReturn("newAccessToken");
        when(jwtService.generateRefreshToken(eq("test@example.com"), eq(testUser.getId().toString())))
                .thenReturn("newRefreshToken");

        // Act
        AuthResponse response = authService.refresh(refreshToken);

        // Assert
        assertNotNull(response);
        assertEquals("newAccessToken", response.token());
        assertEquals("newRefreshToken", response.refreshToken());

        verify(jwtService, times(1)).parse(refreshToken);
        verify(jwtService, times(1)).isRefreshToken(claims);
        verify(repository, times(1)).findByEmailAndDeletedAtIsNull("test@example.com");
        verify(jwtService, times(1)).generateAccessToken(eq("test@example.com"), eq(testUser.getId().toString()), anyList());
        verify(jwtService, times(1)).generateRefreshToken(eq("test@example.com"), eq(testUser.getId().toString()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowExceptionWhenRefreshTokenIsNotRefreshType() {
        // Arrange
        String refreshToken = "invalidToken";
        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = mock(io.jsonwebtoken.Jws.class);
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);

        when(jwtService.parse(refreshToken)).thenReturn(jws);
        when(jws.getPayload()).thenReturn(claims);
        when(claims.get("type")).thenReturn("access");
        when(jwtService.isRefreshToken(claims)).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.refresh(refreshToken));

        verify(jwtService, times(1)).parse(refreshToken);
        verify(jwtService, times(1)).isRefreshToken(claims);
        verify(repository, never()).findByEmailAndDeletedAtIsNull(anyString());
    }

    @Test
    void shouldGetCurrentUser() {
        // Arrange
        String email = "test@example.com";
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(repository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.of(testUser));

        // Act
        var response = authService.getCurrentUser();

        // Assert
        assertNotNull(response);
        assertEquals(testUser.getId(), response.id());
        assertEquals("test@example.com", response.email());
        assertEquals("John", response.firstName());
        assertEquals("Doe", response.lastName());
        assertEquals("EMPLOYEE", response.role());
        assertTrue(response.isActive());

        verify(repository, times(1)).findByEmailAndDeletedAtIsNull(email);

        SecurityContextHolder.clearContext();
    }
}

