package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.AuthResponse;
import com.msme.cashflowguard.dto.LoginRequest;
import com.msme.cashflowguard.dto.RegisterRequest;
import com.msme.cashflowguard.entity.AppUser;
import com.msme.cashflowguard.exception.ConflictException;
import com.msme.cashflowguard.exception.UnauthorizedException;
import com.msme.cashflowguard.repository.AppUserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Register and login. Passwords are stored only as BCrypt hashes. */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("This email is already registered. Try logging in.");
        }
        AppUser user = new AppUser();
        user.setName(request.name().trim());
        String biz = request.businessName() == null ? "" : request.businessName().trim();
        user.setBusinessName(biz.isEmpty() ? null : biz);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setCreatedAt(LocalDateTime.now(clock));
        return toResponse(users.save(user));
    }

    public AppUser authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Incorrect email or password."));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Incorrect email or password.");
        }
        return user;
    }

    public AuthResponse toResponse(AppUser user) {
        return new AuthResponse(user.getId(), user.getName(), user.getEmail(), user.getBusinessName());
    }

    public AuthResponse getUser(Long userId) {
        return toResponse(users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Please login to continue.")));
    }
}
