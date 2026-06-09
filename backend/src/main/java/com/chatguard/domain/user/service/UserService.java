package com.chatguard.domain.user.service;

import com.chatguard.domain.user.dto.UserLoginResponse;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.auth.JwtProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public UserService(UserRepository userRepository, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public UserLoginResponse login(String username) {
        String normalizedUsername = normalizeUsername(username);
        User user = userRepository.findByUsername(normalizedUsername)
            .orElseGet(() -> userRepository.save(User.builder()
                .username(normalizedUsername)
                .displayName(normalizedUsername)
                .build()));

        return new UserLoginResponse(
            new UserLoginResponse.UserDto(user.getId(), user.getUsername(), user.getDisplayName()),
            jwtProvider.createToken(user)
        );
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }
        return username.trim();
    }

}
