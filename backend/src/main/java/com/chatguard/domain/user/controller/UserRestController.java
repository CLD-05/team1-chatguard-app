package com.chatguard.domain.user.controller;

import com.chatguard.domain.user.dto.UserLoginRequest;
import com.chatguard.domain.user.dto.UserLoginResponse;
import com.chatguard.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;

    @PostMapping({"/login", "/auth/login"})
    public ResponseEntity<UserLoginResponse> login(@RequestBody UserLoginRequest request) {
        UserLoginResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

}
