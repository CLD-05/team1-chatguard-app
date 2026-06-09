package com.chatguard.domain.user.service;

import com.chatguard.domain.user.dto.UserLoginRequest;
import com.chatguard.domain.user.dto.UserLoginResponse;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.auth.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Transactional
    public UserLoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .username(request.username())
                                .displayName(request.username()) // 임시로 username을 표시명으로 사용
                                .build()
                ));

        String token = jwtProvider.generateToken(user.getId());

        return new UserLoginResponse(user.getId(), token);
    }
}