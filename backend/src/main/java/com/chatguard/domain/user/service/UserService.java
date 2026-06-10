package com.chatguard.domain.user.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatguard.domain.user.dto.UserLoginRequest;
import com.chatguard.domain.user.dto.UserLoginResponse;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.auth.JwtProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Transactional
    public UserLoginResponse login(UserLoginRequest request) {
        String username = normalizeUsername(request.username());
        User user;

        try {
            user = userRepository.findByUsername(username)
                    .orElseGet(() -> userRepository.save(
                            User.builder()
                                    .username(username)
                                    .displayName(username)
                                    .build()));
        } catch (DataIntegrityViolationException e) {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("동시성 로그인 처리 실패"));
        }

        return new UserLoginResponse(
                new UserLoginResponse.UserDto(user.getId(), user.getUsername(), user.getDisplayName()),
                jwtProvider.createToken(user));
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }
        return username.trim();
    }
}
