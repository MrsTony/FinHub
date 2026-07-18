# dev 模式 /api/** 免密调试 + Banner 调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 开发模式（默认 profile = 非 prod）下 Knife4j 调试 `/api/**` 免 Basic Auth；StartupBanner 反映免密状态并去掉硬编码 dev 密码；prod 鉴权不变。

**Architecture:** 在 `SecurityConfig` 非 prod 分支增加 `/api/**` permitAll（与既有 `DOC_PATHS` 放行并列），prod 分支不变（`/api/**` 落到 `anyRequest().authenticated()`，未认证 401）。401 契约保留在 prod，由既有 `Knife4jProdDisableTest` 覆盖。`StartupBanner.buildPanel` 非 prod 面板的 `Basic Auth : admin/dev-pass` 行改为 `API 调试:免密`。

**Tech Stack:** Spring Boot 3.2.7、Spring Security、JUnit 5 + MockMvc + AssertJ。

## Global Constraints

- 远程 MySQL `121.89.92.242:3306/finhub` 可达（`@SpringBootTest` 加载全上下文需连库）；不可达时集成测按 `assumeTrue` 跳过（`FinHubApplicationTest` 例外，会硬失败）。
- 全程 TDD：每个 task 先调整测试契约（RED）-> 最小实现（GREEN）-> 全量 `mvn test` 回归。
- 遵循 dgg-spring-style：构造器注入、中文 Javadoc/日志、Knife4j 注解。
- commit message 用 gitmoji + 中文（项目历史惯例）。
- `finhub.encryption.key` 必须 32 字节等既有约束不变。

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `src/main/java/com/finhub/infra/config/SecurityConfig.java` | 修改 | 非 prod 分支增加 `/api/**` permitAll |
| `src/test/java/com/finhub/infra/config/Knife4jDevAccessTest.java` | 修改 | `/api/**` 用例从 401 翻转为免密可达 |
| `src/test/java/com/finhub/fundflow/interfaces/IngestionControllerTest.java` | 修改 | 删除 `shouldReturn401WithoutAuth` + Javadoc |
| `src/main/java/com/finhub/infra/config/StartupBanner.java` | 修改 | 非 prod 面板 `Basic Auth` 行 -> `API 调试:免密` |
| `src/test/java/com/finhub/infra/config/StartupBannerTest.java` | 修改 | 对应断言更新 |
| `README.md` | 修改 | 同步 dev 免密调试说明 |

---

## Task 1: SecurityConfig 非 prod 放行 /api/** + dev 测试翻转

**Files:**
- Modify: `src/main/java/com/finhub/infra/config/SecurityConfig.java`
- Modify: `src/test/java/com/finhub/infra/config/Knife4jDevAccessTest.java`
- Modify: `src/test/java/com/finhub/fundflow/interfaces/IngestionControllerTest.java`

**Interfaces:**
- Consumes: 既有 `SecurityConfig.isProd()`、`DOC_PATHS`；既有 `IngestionController` POST `/api/transactions/import`（空文件 -> 控制器 IAE -> 400）
- Produces: 非 prod 下 `/api/**` permitAll（Knife4j 免密调试）

- [ ] **Step 1: 翻转 `Knife4jDevAccessTest` 的 `/api/**` 用例**

在 `Knife4jDevAccessTest.java` 顶部 import 区新增两行（既有 `get` 等 import 保留，`/doc.html` 用例仍用 `get`）：

```java
import org.springframework.mock.web.MockMultipartFile;
```
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
```

把 `apiShouldStillRequireAuthInDev` 整段替换为：

```java
    @Test
    @DisplayName("非 prod：/api/** 免认证可达（空文件 POST 返回 400 而非 401，证明 Security 放行）")
    void apiShouldBeAccessibleWithoutAuthInDev() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "alipay.csv", "text/csv", new byte[0]);
        mockMvc.perform(multipart("/api/transactions/import").file(empty))
                .andExpect(status().isBadRequest());  // 空文件 -> 控制器 IAE -> 400，未走 401
    }
```

- [ ] **Step 2: 删除 `IngestionControllerTest.shouldReturn401WithoutAuth`**

删除 `IngestionControllerTest.java` 中整个 `shouldReturn401WithoutAuth` 方法（含 `@Test`/`@DisplayName` 注解）：

```java
    @Test
    @DisplayName("未认证请求应返回 401")
    void shouldReturn401WithoutAuth() throws Exception {
        MockMultipartFile file = csvFile("alipay.csv", "content");

        mockMvc.perform(multipart("/api/transactions/import").file(file))
                .andExpect(status().isUnauthorized());
    }
```

并把类 Javadoc 验证列表中的「401 未认证」去掉。原行：

```
 * <p>验证：200 上传成功返回计数、400 文件空/文件名空/AppService 抛 IAE、401 未认证、500 意外异常。</p>
```
改为：

```
 * <p>验证：200 上传成功返回计数、400 文件空/文件名空/AppService 抛 IAE、500 意外异常。</p>
```

（`USERNAME`/`PASSWORD` 常量保留，其余 5 个用例仍用 `httpBasic`。）

- [ ] **Step 3: 运行测试，确认 RED**

Run: `mvn test -Dtest=Knife4jDevAccessTest,IngestionControllerTest`
Expected: `Knife4jDevAccessTest.apiShouldBeAccessibleWithoutAuthInDev` FAIL（当前 Security 仍要求认证，POST 空文件无 auth 返回 401，断言 isBadRequest 失败）；`Knife4jDevAccessTest.docHtmlShouldBeAccessibleWithoutAuthInDev` PASS；`IngestionControllerTest` 5 个用例 PASS（401 已删，其余 authed）。失败原因是 Security 未放行 `/api/**`，非编译错误。

- [ ] **Step 4: 改造 `SecurityConfig` 非 prod 分支放行 `/api/**`**

把 `securityFilterChain` 内 `authorizeHttpRequests` 段落改为（仅非 prod 分支新增一行 `/api/**` permitAll）：

```java
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health").permitAll();  // 健康检查免认证
                if (!isProd()) {
                    auth.requestMatchers(DOC_PATHS).permitAll();        // 非 prod 放行文档页
                    auth.requestMatchers("/api/**").permitAll();       // dev 免密调试
                }
                auth.anyRequest().authenticated();
            })
```

prod 分支不变（`isProd()` 为 true 时跳过 `DOC_PATHS` 与 `/api/**` 放行，落到 `anyRequest().authenticated()`）。

- [ ] **Step 5: 运行测试，确认 GREEN**

Run: `mvn test -Dtest=Knife4jDevAccessTest,IngestionControllerTest`
Expected: `Knife4jDevAccessTest` 2 用例 PASS（`/doc.html` 200；`/api/**` 空文件无 auth -> 400）；`IngestionControllerTest` 5 用例 PASS。

- [ ] **Step 6: 回归**

Run: `mvn test`
Expected: 全绿，229 个测试（原 230 删 1 个 dev-401）。无回归（含 `Knife4jProdDisableTest` prod 侧 `/api/**` 仍 401）。

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/finhub/infra/config/SecurityConfig.java src/test/java/com/finhub/infra/config/Knife4jDevAccessTest.java src/test/java/com/finhub/fundflow/interfaces/IngestionControllerTest.java
git commit -m "✨ feat(infra): dev 模式 /api/** 免密调试（非 prod 放行）"
```

---

## Task 2: StartupBanner dev 面板改为免密标识

**Files:**
- Modify: `src/main/java/com/finhub/infra/config/StartupBanner.java`
- Modify: `src/test/java/com/finhub/infra/config/StartupBannerTest.java`

**Interfaces:**
- Consumes: 既有 `StartupBanner.buildPanel(isProd, appName, version, port, contextPath, lanIp)` 纯静态方法
- Produces: 非 prod 面板含 `API 调试:免密`，不再含 `admin/dev-pass`

- [ ] **Step 1: 更新 `StartupBannerTest` 断言**

把 `shouldContainLinksAndCredentialsWhenNotProd` 整段替换为（重命名 + 断言翻转）：

```java
    @Test
    @DisplayName("非 prod：面板应含本地/局域网 doc.html 链接、API 端点、免密标识、应用名")
    void shouldContainLinksAndNoAuthInfoWhenNotProd() {
        String panel = StartupBanner.buildPanel(
                false, "FinHub", "0.1.0", 8080, "", "192.168.1.100");

        assertThat(panel)
                .contains("FinHub")
                .contains("0.1.0")
                .contains("http://localhost:8080/doc.html")
                .contains("http://192.168.1.100:8080/doc.html")
                .contains("/api/transactions/import")
                .contains("API 调试")
                .contains("免密")
                .doesNotContain("admin/dev-pass");
    }
```

`shouldNotContainLinksAndCredentialsWhenProd`、`shouldIncludeContextPathInLinks`、`shouldHandleLanIpNA` 三个用例不变（prod 用例 `doesNotContain("admin/dev-pass")` 仍成立）。

- [ ] **Step 2: 运行测试，确认 RED**

Run: `mvn test -Dtest=StartupBannerTest`
Expected: `shouldContainLinksAndNoAuthInfoWhenNotProd` FAIL（当前面板含 `admin/dev-pass`、不含 `免密`，断言失败）。失败原因是 Banner 文案未改，非编译错误。

- [ ] **Step 3: 改 `StartupBanner.buildPanel` 非 prod 面板文案**

把非 prod 分支的文本块中这一行：

```
                  Basic Auth  : admin/dev-pass
```
改为：

```
                  API 调试  : 免密
```

（其余行：标题 `FinHub v0.1.0`、环境、API 文档、局域网文档、API 端点、底部分隔线不变。prod 分支不变。）

- [ ] **Step 4: 运行测试，确认 GREEN**

Run: `mvn test -Dtest=StartupBannerTest`
Expected: 4 用例全 PASS。

- [ ] **Step 5: 回归**

Run: `mvn test`
Expected: 全绿，229 个测试，无回归。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/finhub/infra/config/StartupBanner.java src/test/java/com/finhub/infra/config/StartupBannerTest.java
git commit -m "✨ feat(infra): StartupBanner dev 面板改为免密标识（去硬编码密码）"
```

---

## Task 3: README 同步 + 全量回归 + 真实启动冒烟

**Files:**
- Modify: `README.md`

**Interfaces:**
- N/A（文档 + 验收）

- [ ] **Step 1: 更新 README 的 Knife4j 节两处文案**

把这一行：

```
- 非 prod 文档页 permitAll，`/api/**` 仍 Basic Auth（Knife4j Authorize 配置 admin/dev-pass 调试）
```
改为：

```
- 非 prod 文档页与 `/api/**` 均 permitAll，Knife4j 免密调试（prod 仍 Basic Auth 鉴权）
```

把这一行：

```
- `StartupBanner`（`ApplicationRunner`）启动打印本地+局域网 doc.html 链接 + 凭据，prod 仅一行
```
改为：

```
- `StartupBanner`（`ApplicationRunner`）启动打印本地+局域网 doc.html 链接 + 免密标识，prod 仅一行
```

- [ ] **Step 2: 全量回归闸门**

Run: `mvn test`
Expected: 229 全绿，零失败零跳过（integration 因 DB 不可达跳过除外）。核对：`Knife4jDevAccessTest`(2) + `Knife4jProdDisableTest`(2) + `OpenApiConfigTest`(1) + `StartupBannerTest`(4) = 9 全绿；`IngestionControllerTest` 5 个用例全绿（已无 401 用例）。

- [ ] **Step 3: 手测 dev 启动（默认 profile）**

Run: `mvn spring-boot:run -Dspring-boot.run.fork=false`（后台运行）
Expected：
- 控制台 Banner 面板含 `API 调试:免密`，**不含** `admin/dev-pass`。
- `curl -s -o /dev/null -w '%{http_code}' -X POST -F "file=@/dev/null" http://localhost:8080/api/transactions/import`（无 auth）返回 `400`（非 401，证明免密放行后到达控制器）。
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/doc.html` 返回 `200`。
- 停止应用（TaskStop；若端口未释放，`netstat -ano | grep :8080 | grep LISTENING` 取 PID 后 `taskkill //F //PID <pid>`）。

- [ ] **Step 4: 手测 prod 启动（验证隔离不变）**

Run: `mvn spring-boot:run -Dspring-boot.run.fork=false -Dspring-boot.run.profiles=prod`（后台运行）
Expected：
- 控制台仅一行 `启动完成（env=prod，Knife4j 已禁用）`，无链接无凭据无「免密」。
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/transactions/import` 返回 `401`。
- 停止应用。

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "📝 docs(infra): README 同步 dev 免密调试说明"
```

---

## 验收清单

- [ ] 非 prod `/api/**` 免认证可达（Knife4j 免密调试 `POST /api/transactions/import`）
- [ ] 非 prod Banner 显示 `API 调试:免密`，不含 `admin/dev-pass`
- [ ] prod `/api/**` 未认证 401（不变）
- [ ] prod Banner 仅一行「Knife4j 已禁用」（不变）
- [ ] `mvn test` 229 全绿，无回归