package com.bizlink.controller;

import com.bizlink.model.User;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody User user) {
        log.info("POST /api/auth/register");
        Map<String, Object> result = authService.register(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", result));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody User credentials) {
        log.info("POST /api/auth/login");
        Map<String, Object> result = authService.login(credentials);
        return ResponseEntity.ok(ApiResponse.success("Login successful", result));
    }
}
