package com.copilot.controller;

import com.copilot.dto.request.RegisterRequest;
import com.copilot.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public void register(@RequestBody RegisterRequest registerRequest) {
        //return null;
    }

    @PostMapping("/login")
    public void login(@RequestBody RegisterRequest registerRequest) {
        //return null;
    }
}
