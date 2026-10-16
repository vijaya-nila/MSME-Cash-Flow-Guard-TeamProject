package com.msme.cashflowguard.config;

import com.msme.cashflowguard.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Protects every /api/** route except login/register/health.
 * The logged-in user id lives in the server-side session; controllers receive it
 * through the request attribute "userId".
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER_ID = "userId";
    public static final String REQUEST_USER_ID = "userId";

    private final AppUserRepository users;

    public AuthInterceptor(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (id instanceof Long userId && users.existsById(userId)) {
            request.setAttribute(REQUEST_USER_ID, userId);
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\","
                + "\"message\":\"Your session has ended. Please login again.\"}");
        return false;
    }
}
