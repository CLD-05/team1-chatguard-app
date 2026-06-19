package com.chatguard.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatguard.domain.user.dto.UserLoginRequest;
import com.chatguard.domain.user.dto.UserLoginResponse;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.auth.JwtProvider;
import com.chatguard.global.error.CustomException;
import com.chatguard.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserLoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        String token = jwtProvider.generateToken(user.getId(), user.getDisplayName(), user.getRole().name());
        return new UserLoginResponse(user.getId(), user.getDisplayName(), user.getRole().name(), token);
    }
}
