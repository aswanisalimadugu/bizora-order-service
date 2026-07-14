package com.bizlink.service;

import com.bizlink.exception.DuplicateException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.User;
import com.bizlink.repository.UserRepository;
import com.bizlink.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public Map<String, Object> register(User user) {
        log.info("Registering user with email: {}", user.getEmail());

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateException("Email already registered");
        }
        if (userRepository.existsByMobile(user.getMobile())) {
            throw new DuplicateException("Mobile number already registered");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ValidationException("Cannot register as admin");
        }
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("OWNER");
        }
        user.setStatus("ACTIVE");

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getId(), saved.getEmail(), saved.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", saved);
        return result;
    }

    @Transactional
    public Map<String, Object> login(User credentials) {
        log.info("Login attempt for email: {}", credentials.getEmail());

        User user = userRepository.findByEmail(credentials.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(credentials.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("Account is not active");
        }

        user.setLastLogin(java.time.LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }
}
