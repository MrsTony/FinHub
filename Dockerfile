# ============================================================================
# FinHub — Dockerfile（多阶段构建）
# 阶段 1：Maven 编译（含依赖缓存层）
# 阶段 2：JRE 运行时镜像（精简体积）
# ============================================================================

# ── 阶段 1：构建 ──
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build
# 先复制 pom.xml 单独解析依赖（利用 Docker 层缓存，反复构建时跳过下载）
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# 再复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B -q

# ── 阶段 2：运行 ──
FROM eclipse-temurin:17-jre-alpine

# 非 root 用户（安全基线）
RUN addgroup -S finhub && adduser -S finhub -G finhub
WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/target/*.jar app.jar

# 健康检查（依赖 Actuator，Spring Boot 3.2 默认暴露 /actuator/health）
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

USER finhub
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]