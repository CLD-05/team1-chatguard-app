# backend (Chat Server) — Spring Boot 3.5 / Java 17
#
# 멀티스테이지: build(JDK+Maven) → runtime(JRE만).
# 테스트는 PR 게이트 ci.yml이 담당하므로 이미지 빌드에선 스킵한다
# (@SpringBootTest는 MySQL·Redis 연결이 필요해 docker build 안에서는 돌 수 없음).
# 런타임 계약(A-5): 포트 8080, /actuator/health · /actuator/prometheus.

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 캐시 적중률을 높인다.
# (pom.xml이 안 바뀌면 go-offline 레이어는 재사용됨)
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package \
    && cp target/*.jar /workspace/app.jar

# ---------- 2단계: 런타임 ----------
FROM eclipse-temurin:17-jre-jammy

# 비루트 실행 (k8s securityContext.runAsNonRoot 대비)
RUN groupadd --system --gid 999 app && useradd --system --uid 999 --gid 999 --no-create-home app
USER 999

WORKDIR /app
COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

# 컨테이너 메모리 한도(k8s resources.limits) 기준으로 힙을 잡는다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# exec form: java가 PID 1이 되어 SIGTERM을 직접 받는다(graceful shutdown 전제 — D15).
ENTRYPOINT ["java", "-jar", "app.jar"]