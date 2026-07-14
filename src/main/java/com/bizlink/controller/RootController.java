package com.bizlink.controller;

import com.bizlink.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<ApiResponse<String>> root() {
        return ResponseEntity.ok(ApiResponse.success("BizLink API is running", "ok"));
    }
}
