package com.chatguard.global.log;

import com.chatguard.domain.admin.service.AdminAuditLogService;
import com.chatguard.domain.moderation.repository.BannedWordRepository;
import com.chatguard.domain.user.entity.User;
import com.chatguard.domain.user.repository.UserRepository;
import com.chatguard.global.auth.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminLogAspect {

    private final AdminAuditLogService adminAuditLogService;
    private final UserRepository userRepository;
    private final BannedWordRepository bannedWordRepository;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(adminLog)")
    public Object logAdminAction(ProceedingJoinPoint joinPoint, AdminLog adminLog) throws Throwable {
        // 1. 현재 어드민 식별 (AuthContext ThreadLocal 및 DB 조회 연동)
        String adminId = resolveAdminId();

        String action = adminLog.value();
        String resourceId = null;

        if ("DELETE_KWD".equals(action)) {
            String rawIdStr = resolveResourceId(joinPoint, adminLog.resourceId());
            if (rawIdStr != null) {
                try {
                    Long wordId = Long.parseLong(rawIdStr);
                    resourceId = bannedWordRepository.findById(wordId)
                            .map(com.chatguard.domain.moderation.entity.BannedWord::getWord)
                            .orElse(rawIdStr);
                } catch (NumberFormatException e) {
                    resourceId = rawIdStr;
                }
            }
        }

        // 2. 비즈니스 로직 실행
        Object result = joinPoint.proceed();

        // 3. 로그 메타데이터 (resourceId, description) 동적 추출
        if (resourceId == null) {
            resourceId = resolveResourceId(joinPoint, adminLog.resourceId());
        }
        String description = resolveDescription(joinPoint);

        // 4. 비동기 저장 서비스 호출
        adminAuditLogService.saveLog(adminId, action, resourceId, description);

        return result;
    }

    private String resolveAdminId() {
        Long userId = AuthContext.getUserId();
        if (userId != null) {
            return userRepository.findById(userId)
                    .map(User::getUsername)
                    .orElse("USER_ID_" + userId);
        }
        return "SYSTEM";
    }

    private String resolveResourceId(ProceedingJoinPoint joinPoint, String spelExpression) {
        if (spelExpression != null && !spelExpression.trim().isEmpty()) {
            try {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                String[] parameterNames = signature.getParameterNames();
                Object[] args = joinPoint.getArgs();

                EvaluationContext context = new StandardEvaluationContext();
                if (parameterNames != null && args != null) {
                    for (int i = 0; i < parameterNames.length; i++) {
                        context.setVariable(parameterNames[i], args[i]);
                    }
                }
                return Objects.toString(parser.parseExpression(spelExpression).getValue(context), null);
            } catch (Exception e) {
                log.warn("Failed to evaluate SpEL expression for resourceId: {}", spelExpression, e);
            }
        }

        // 폴백: SpEL 평가에 실패했거나 SpEL이 비어있는 경우, 메서드 매개변수 중 id나 roomId 등 탐색
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames != null && args != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                String paramName = parameterNames[i];
                Object arg = args[i];
                if (arg == null) continue;

                if ("id".equalsIgnoreCase(paramName) || "roomId".equalsIgnoreCase(paramName) || "userId".equalsIgnoreCase(paramName)) {
                    return arg.toString();
                }
            }
        }

        // DTO 인자 검색 및 핵심 필드 추출
        if (args != null) {
            for (Object arg : args) {
                if (arg == null) continue;
                String className = arg.getClass().getSimpleName();
                if (className.endsWith("Request")) {
                    try {
                        Method getWord = arg.getClass().getMethod("getWord");
                        return Objects.toString(getWord.invoke(arg));
                    } catch (Exception ignored) {
                        try {
                            Method getId = arg.getClass().getMethod("getId");
                            return Objects.toString(getId.invoke(arg));
                        } catch (Exception ignored2) {
                            return className;
                        }
                    }
                }
            }
        }

        return null;
    }

    private String resolveDescription(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StringBuilder sb = new StringBuilder();
        sb.append("Executed: ").append(methodName);
        if (args != null && args.length > 0 && parameterNames != null) {
            sb.append(" with arguments: ");
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) continue;

                String paramName = parameterNames[i];
                String className = arg.getClass().getSimpleName();
                if (className.contains("Authentication") || className.contains("HttpServlet") || "userId".equals(paramName)) {
                    continue;
                }

                // 민감 파라미터 마스킹 처리 (password, token, secret, credential, key)
                Object displayValue = arg;
                String lowerParam = paramName.toLowerCase();
                if (lowerParam.contains("password") || lowerParam.contains("token") ||
                        lowerParam.contains("secret") || lowerParam.contains("credential") ||
                        lowerParam.contains("key")) {
                    displayValue = "******";
                }

                sb.append("[").append(paramName).append("=").append(displayValue).append("] ");
            }
        }

        return sb.toString().trim();
    }
}
