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
        User user;
        try {
            user = userRepository.findByUsername(request.username())
                    .orElseGet(() -> userRepository.save(
                            User.builder().username(request.username()).displayName(request.username()).build()
                    ));
        } catch (DataIntegrityViolationException e) {
            user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new RuntimeException("동시성 로그인 처리 실패"));
        }

        String token = jwtProvider.generateToken(user.getId());
        return new UserLoginResponse(user.getId(), token);
    }
}
