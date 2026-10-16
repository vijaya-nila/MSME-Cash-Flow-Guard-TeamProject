package com.msme.cashflowguard.controller;

import com.msme.cashflowguard.config.AuthInterceptor;
import com.msme.cashflowguard.dto.AuthResponse;
import com.msme.cashflowguard.dto.LoginRequest;
import com.msme.cashflowguard.dto.RegisterRequest;
import com.msme.cashflowguard.entity.AppUser;
import com.msme.cashflowguard.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AppUser user = authService.authenticate(request);
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate();                       // prevents session fixation
        }
        HttpSession session = http.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
        return authService.toResponse(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthResponse me(@RequestAttribute("userId") Long userId) {
        return authService.getUser(userId);
    }
}
