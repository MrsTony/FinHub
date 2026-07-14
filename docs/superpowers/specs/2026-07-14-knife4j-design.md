# Knife4j 集成 + 环境隔离 + 启动 Banner 设计

> 日期：2026-07-14
> 上下文：FinHub Day5 已实现 `IngestionController`，控制器已带 OpenAPI 注解（`@Tag/@Operation/@Parameter`），但项目仅有原生 springdoc，无 Knife4j 增强 UI，无 profile 机制，无启动 Banner。本设计引入 Knife4j 方便测试环境查看与调试接口，并严格保证生产环境不可访问。

---

## 1. 目标与约束

### 目标
- **测试/开发环境**：启动后访问 `/doc.html` 查看接口文档、在线调试，启动控制台打印访问链接
- **生产环境**：Knife4j 与 OpenAPI 文档资源**完全不可访问**（禁用 + 拒绝双保险）
- **启动 Banner**：服务启动后控制台打印中文 ASCII 面板（地址、API 端点、文档地址、凭据），仅测试环境打印链接与凭据

### 约束（已定决策）
1. 环境识别机制 = **Spring profile `prod`**（非 prod 即可访问）
2. 非 prod 时 Knife4j 文档页**免认证**（permitAll）；`/api/**` 真实接口仍 Basic Auth（Knife4j Authorize 配置）
3. 遵循 dgg-spring-style：构造器注入、中文注释/日志、ApplicationRunner 服务面板（H6）

---

## 2. 依赖变更

### 新增
- `com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:4.5.0`
  - Knife4j 4.x，Spring Boot 3 / Jakarta 专用版本
  - 提供 `/doc.html` 增强文档 UI

### 保留
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`（现有）
  - Knife4j 在 springdoc 之上增强，二者共存是官方推荐标准做法
  - `IngestionController` 已有 OpenAPI 注解无需改动，直接在 `/doc.html` 渲染

---

## 3. 环境隔离（profile prod）

### `application.yml`（默认/dev/test）
- 不含 `knife4j.production`
- Knife4j + springdoc 正常启用

### 新增 `application-prod.yml`
```yaml
knife4j:
  production: true              # Knife4j 原生生产模式，屏蔽所有 Knife4j 资源接口
springdoc:
  api-docs:
    enabled: false              # 关闭原生 OpenAPI JSON（/v3/api-docs）
  swagger-ui:
    enabled: false              # 关闭 swagger-ui
```

### 启动方式
- 生产：`--spring.profiles.active=prod`（或 Docker `SPRING_PROFILES_ACTIVE=prod`）
- 默认（无 profile / dev / test）：Knife4j 可用

### OpenAPI 元数据 Bean
- 新增 `OpenApiConfig`，标 `@Profile("!prod")`
- prod 下根本不创建 OpenAPI 元数据 Bean，从源头杜绝文档生成

---

## 4. Security 路由改造（SecurityConfig）

注入 `org.springframework.core.env.Environment`，按是否 `prod` profile 分支：

### 非 prod
```
/doc.html               permitAll
/swagger-ui/**          permitAll
/swagger-resources/**   permitAll
/v3/api-docs/**         permitAll
/webjars/**             permitAll
/actuator/health        permitAll
其余（含 /api/**）       authenticated
```

### prod
- 文档路径**不**进入 permitAll，落到 `authenticated`
- 配合配置层禁用（第 3 节）形成 404/401 双保险

### 判定方法
`env.acceptsProfiles(Profiles.of("prod"))`

### `/api/**` 认证
- 始终 Basic Auth
- Knife4j 调试真实接口时，在「Authorize」里配置 Basic Auth（admin/dev-pass）

---

## 5. 启动 Banner（新增 `infra/config/StartupBanner`）

### 实现方式
- `@Component` + `implements ApplicationRunner`
- 注入 `Environment`、`@Value("${server.port:8080}")`、`@Value("${server.servlet.context-path:}")`
- 面板字符串构建抽成**纯方法** `buildPanel(env, port, contextPath)`，便于单测

### 面板内容（非 prod）
中文 ASCII 等宽面板，包含：
- 应用名 + 版本（FinHub 0.1.0）
- 当前环境（profile）
- 本地地址：`http://localhost:{port}/doc.html`
- 局域网地址：`http://{局域网IP}:{port}/doc.html`（`InetAddress.getLocalHost()`，失败回退 `N/A`）
- API 端点：`http://localhost:{port}/api/transactions/import`
- Basic Auth 凭据：`admin/dev-pass`（仅非 prod 打印）

### 面板内容（prod）
仅一行：
```
FinHub 启动完成（env=prod，Knife4j 已禁用）
```
**不打印链接与凭据**。

### 安全注意
- 凭据 `dev-pass` 是 SecurityConfig 未注入密码时的兜底值，仅测试环境打印
- 局域网 IP 获取失败不阻断启动，打印 `N/A`

---

## 6. OpenAPI 元数据增强（`OpenApiConfig`，`@Profile("!prod")`）

```java
@Configuration
@Profile("!prod")
public class OpenApiConfig {
    @Bean
    public OpenAPI finHubOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("FinHub API")
                .description("个人资金数据治理中台 - 接口文档")
                .version("0.1.0"))
            .components(new Components()
                .addSecuritySchemes("BasicAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")));
    }
}
```

- 让 Knife4j「Authorize」可配置 Basic Auth，调试 `/api/**` 真实接口
- prod 下不创建，无文档元数据

---

## 7. 测试

### 新增测试
1. **`StartupBannerTest`**（纯单测）
   - 断言 `buildPanel` 非 prod 输出含 `/doc.html` URL、`admin/dev-pass`、应用名
   - 断言 prod 输出含「Knife4j 已禁用」且**不含** `doc.html`、不含凭据
   - 不依赖控制台输出

2. **`Knife4jProductionSecurityTest`**（`@SpringBootTest @ActiveProfiles("prod")`）
   - `/doc.html` 返回非 200（禁用生效）
   - `/v3/api-docs` 返回非 200
   - 验证 OpenAPI Bean 在 prod 下不存在（`ApplicationContext` 中无 `OpenAPI` bean）

3. **`Knife4jDevAccessTest`**（`@SpringBootTest` 默认 profile）
   - 非 prod 时 `/doc.html` permitAll（验证免认证可访问，或返回 200/重定向）
   - `/api/**` 仍需 Basic Auth（不回归现有 Controller 测试）

### 现有测试影响
- `IngestionControllerTest`（默认 profile）不受影响，API 仍 Basic Auth
- 全量 `mvn test` 回归闸门

---

## 8. 验收清单

- [ ] 非 prod 启动后控制台打印 Banner，含本地+局域网 `/doc.html` 链接 + 凭据
- [ ] 非 prod 访问 `/doc.html` 免认证可打开，文档渲染 `IngestionController` 接口
- [ ] 非 prod Knife4j「Authorize」配 Basic Auth 后可调试 `/api/transactions/import`
- [ ] prod 启动后控制台仅打印一行，无链接无凭据
- [ ] prod 访问 `/doc.html`、`/v3/api-docs` 均非 200
- [ ] `mvn test` 全绿，无回归

---

## 9. 已排除方案

1. **仅 `knife4j.enable=false` 单点关闭**：端点仍可能漏（OpenAPI JSON 默认可访问），不符合「禁止访问」语义
2. **仅靠 `@Profile` 不加载 Knife4j 自动配置**：Knife4j starter 的自动配置无法简单按 profile 跳过，需「配置禁用 + 安全拒绝」才可靠

---

## 10. 文件清单

| 文件 | 操作 |
|------|------|
| `pom.xml` | 新增 knife4j 依赖 |
| `src/main/resources/application.yml` | 无改动（默认即非 prod） |
| `src/main/resources/application-prod.yml` | 新增 |
| `src/main/java/com/finhub/infra/config/SecurityConfig.java` | 改造路由分支 |
| `src/main/java/com/finhub/infra/config/StartupBanner.java` | 新增 |
| `src/main/java/com/finhub/infra/config/OpenApiConfig.java` | 新增 |
| `src/test/java/com/finhub/infra/config/StartupBannerTest.java` | 新增 |
| `src/test/java/com/finhub/infra/config/Knife4jProductionSecurityTest.java` | 新增 |
| `src/test/java/com/finhub/infra/config/Knife4jDevAccessTest.java` | 新增 |
