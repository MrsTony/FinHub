# dev 模式 /api/** 免密调试 + Banner 调整 设计

**日期**:2026-07-18
**背景**:Knife4j 集成完成后,开发模式下用 `/doc.html` 调试 `/api/**` 仍需在 Authorize 输入 Basic Auth(admin/dev-pass),体验冗余。希望开发模式直接免密调试,生产环境鉴权不变。

## 目标

- 开发模式(默认 profile = 非 prod)下,Knife4j 调试 `/api/**` 无需 Basic Auth。
- 生产环境(prod profile)`/api/**` 鉴权不变(未认证 401)。
- 启动 Banner 反映 dev 免密状态,不再硬编码打印 dev 密码 `admin/dev-pass`。

## 方案选型

采用 **方案 A(profile 驱动)**,不引入新配置属性:

- 非 prod 分支放行 `/api/**`(与既有 `DOC_PATHS` 放行并列)。
- prod 分支不变(`/api/**` 落到 `anyRequest().authenticated()`,未认证 401)。
- 401 契约保留在 prod,由既有 `Knife4jProdDisableTest` 覆盖。

放弃方案 D(配置开关 `finhub.auth.enabled`):D 更灵活但多一层配置与 `@Value`,当前需求仅"dev 免密、prod 强制开",profile 驱动已足够,且 prod 鉴权由 `!isProd()` 结构性保证。

## 改动清单

### 1. `SecurityConfig`(改)

非 prod 分支增加 `/api/**` permitAll:

```java
.authorizeHttpRequests(auth -> {
    auth.requestMatchers("/actuator/health").permitAll();
    if (!isProd()) {
        auth.requestMatchers(DOC_PATHS).permitAll();
        auth.requestMatchers("/api/**").permitAll();   // dev 免密调试
    }
    auth.anyRequest().authenticated();
})
```

prod 分支不变。`isProd()` 既有逻辑(`environment.acceptsProfiles(Profiles.of("prod"))`)不变。

### 2. `Knife4jDevAccessTest`(改 `/api/**` 用例)

`apiShouldStillRequireAuthInDev` 翻转为 `apiShouldBeAccessibleWithoutAuthInDev`:

```java
@Test
@DisplayName("非 prod：/api/** 免认证可达（空文件 POST 返回 400 而非 401，证明 Security 放行）")
void apiShouldBeAccessibleWithoutAuthInDev() throws Exception {
    MockMultipartFile empty = new MockMultipartFile("file", "alipay.csv", "text/csv", new byte[0]);
    mockMvc.perform(multipart("/api/transactions/import").file(empty))
            .andExpect(status().isBadRequest());  // 空文件 -> 控制器 IAE -> 400，未走 401
}
```

- 选 POST 空文件:控制器内 `file.isEmpty()` 即抛 IAE(经 `GlobalExceptionHandler` -> 400),不触达 `IngestionAppService`,无需 mock,确定性高。
- `/doc.html` 用例(`docHtmlShouldBeAccessibleWithoutAuthInDev`)不变。

### 3. `IngestionControllerTest`(删 401 用例)

- 删除 `shouldReturn401WithoutAuth`:dev(默认 profile)已不 401,前提失效。
- Javadoc 验证列表去掉"401 未认证"。
- 其余 5 个用例(200 / 400×3 / 500)都用 `httpBasic`,dev 放行后仍通过。
- 401 契约保留在 prod(既有 `Knife4jProdDisableTest.apiShouldStillRequireAuthInProd`)。

### 4. `StartupBanner`(改 Banner 文案)

非 prod 面板行 `Basic Auth  : admin/dev-pass` 改为 `API 调试  : 免密`:

```java
  API 调试  : 免密
```

- 去掉硬编码的 dev 密码 `admin/dev-pass`(符合"避免硬编码")。
- dev 下 Basic Auth 仍有效(只是非必需),creds 不再打印。
- prod 面板不变(仍"Knife4j 已禁用")。

`StartupBannerTest` 对应更新:
- `shouldContainLinksAndCredentialsWhenNotProd` 重命名为 `shouldContainLinksAndNoAuthInfoWhenNotProd`,断言 `.contains("API 调试")` `.contains("免密")`,并 `.doesNotContain("admin/dev-pass")`。
- `shouldNotContainLinksAndCredentialsWhenProd` 断言不变(仍 `doesNotContain("admin/dev-pass")`,继续成立)。

### 5. 不动

- `OpenApiConfig`(BasicAuth scheme,`@Profile("!prod")`):dev 免密下 Authorize 非必需但无害,prod 不加载。最小改动原则保留。
- `application-prod.yml`、`Knife4jProdDisableTest`(prod 侧契约)。

## 测试影响与验收

- 测试数:230 -> 229(删 `IngestionControllerTest` 1 个 dev-401 用例;其余为改写/文案,数量不变)。
- TDD:每处改动先调整测试契约(RED)-> 改实现(GREEN)-> `mvn test` 全量回归。
- 验收:
  - `mvn test` 229 全绿。
  - 真实启动 dev:`/api/transactions/import` 无需 Authorize 即可调试;Banner 显示"API 调试:免密",不含 `admin/dev-pass`。
  - 真实启动 prod:`/api/**` 未认证 401(不变);Banner 仅"Knife4j 已禁用"。

## 风险

- 非 prod 下 `/api/**` 完全开放:与既有 `/doc.html` 非 prod 放行一致,仅本地/测试环境,prod 仍锁死。可接受。
- Banner 文案中文字宽对齐:控制台 Banner 不强求严格对齐,测试只校验 `contains`,无影响。