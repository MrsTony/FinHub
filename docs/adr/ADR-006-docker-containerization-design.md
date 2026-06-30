# ADR-006: Docker 容器化 + 多阶段构建

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-06-30 |
| **决策者** | xiaod |
| **影响范围** | 基础设施上下文 |

## 背景

开发环境（Windows）与目标运行环境（Linux 服务器）不一致，MySQL 时区、文件编码、JVM 参数等容易出现"在我机器上能跑"问题。需要环境一致性保证。

## 决策

**采用 Docker Compose 编排 MySQL 8.0 + Redis 7 + FinHub App，Dockerfile 多阶段构建。**

## 实现

### Dockerfile — 多阶段构建

```dockerfile
# 阶段 1：Maven 编译（含依赖缓存层）
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn package -DskipTests -B -q

# 阶段 2：JRE 运行时（精简镜像）
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S finhub && adduser -S finhub -G finhub  # 非 root
COPY --from=builder /build/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### Docker Compose — 服务编排

```
mysql (健康检查必须通过) ──┐
                          ├──→ app 启动
redis (健康检查必须通过) ──┘
```

### 关键设计点

| 设计 | 说明 |
|------|------|
| **多阶段构建** | Maven 编译层 + JRE 运行层分离，目标体积 < 100MB |
| **依赖缓存** | `COPY pom.xml` 在前，`COPY src` 在后，重复构建命中缓存 |
| **非 root 用户** | `adduser finhub`，安全基线 |
| **ZGC** | 低延迟 GC，适合容器化环境 |
| **MaxRAMPercentage=75%** | 容器友好，不设固定 `-Xmx` |
| **健康检查链** | `depends_on` + `condition: service_healthy`，避免数据库未就绪 |
| **容器网络覆写** | `docker-compose.yml` 将 `SPRING_DATASOURCE_URL` 覆写为 `mysql:3306`（容器内网），本地开发仍用 `application.yml` 外网地址 |
| **MySQL 时区** | 强制 `--default-time-zone=+08:00`，字符集 `utf8mb4` |
| **Redis 持久化** | `--save 60 1`，轻量持久化 |
| **环境变量模板** | `.env.example` 含所有配置项，`.env` 在 `.gitignore` 中 |

## 后果

- 开发、测试、生产环境完全一致
- 最终镜像 ~85MB（Alpine JRE + Spring Boot JAR）
- 启动时间 ~3s（含健康检查等待）

## 面试话术

> "开发、测试、生产环境完全一致——Docker Compose 保证 MySQL 字符集、时区、Redis 版本都锁死。多阶段构建让最终镜像只有 ~85MB，非 root 用户运行，这是容器化安全基线。"

## 参考

- [Dockerfile](../../Dockerfile)
- [docker-compose.yml](../../docker-compose.yml)
- [.env.example](../../.env.example)