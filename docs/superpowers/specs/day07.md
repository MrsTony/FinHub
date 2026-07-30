以下是 **Day 7 设计文档**。Day 6 已闭环领域事件（252 测试全绿）。本日转入**数据治理 + CI/DI**——把行动指南 Phase 7「数据资产与代码同等重要」落地：Prompt/Golden Set 用 DVC 版本化、`PromptRegistry` 补文件系统实现、Golden Set 骨架对规则层跑通评测、搭 GitHub Actions CI。

> 三项已定决策（DGG 拍板）：① 范围 = **数据治理 + CI/DI**（day06 预告的查询/MCP 上下文顺延 Day8+）；② DVC 用**本地 remote** 起步（最简可跑，不进云存储），能演示 `dvc repro`/`dvc diff`；③ Golden Set = **骨架 + 文档**，先对规则层 `TransactionClassifier` 跑通部分用例，NL 部分留 TODO（LLM 未接通）。
>
> 执行计划见 `docs/superpowers/plans/2026-07-30-day07-data-governance-ci.md`。

---

## 🔒 TDD 铁律（全局约束，适用于 Day7 全部代码任务）

> 与 Day5/Day6 一致，此处摘要；完整版见 `day05.md`。

**核心规则**：任何新类创建或旧类修改，**必须先写测试契约**。无测试契约，不执行任何代码改动。本日代码任务仅 Task 3（`FileSystemPromptRegistry`）与 Task 5（`GoldenSetEvalTest`）；Task 1/2/4/6 为文档、DVC、数据、CI 配置，非 TDD 代码。

### 三段式执行流程

```
RED    先写测试契约，跑 mvn test -Dtest=<TestClass>
       -> 必须看到测试因「实现缺失 / 行为未达成」而 FAILED（非编译错误）
GREEN  写最小实现让测试通过 -> PASS
回归   跑全量 mvn test -> 改动的 + 之前所有测试契约必须全绿
```

### Day7 特有约束

- **DVC 边界**：`prompts/`、`golden-set/` 由 DVC 管，Git 只提交指针（`.dvc/`、`*.dvc`、`dvc.yaml`、`.dvcignore`）。本地 remote `data/dvc-storage` gitignore，不进库。
- **CI 不连远程 MySQL**：全量 `mvn test` 连库；CI 用「编译 + 纯单测」口径排除连库集成测，连库测只在本地跑。
- **不启用** pom 注释的 `spring-ai`/`jsqlparser`（LLM 未接通）；Eval 只跑规则层。
- **禁止改既有签名**：`PromptRegistry` 接口、`NLTranslator` 及 record、`TransactionClassifier`、`Transaction`、建表 SQL。

---

## 📋 Day 7 目标

| 模块 | 产出 | 缺口解除 |
| ---- | ---- | -------- |
| ADR-007 | 数据资产版本化决策记录 | 行动指南 ADR-004(DVC) 未落地 |
| DVC 配置 | init + 本地 remote + `prompts/`/`golden-set/` 入 DVC | 数据资产无版本化 |
| `FileSystemPromptRegistry` | 从 `prompts/` 加载模板 + 变量替换 + 单测 | 接口无实现、Prompt 无来源 |
| Golden Set | `classifier-cases.jsonl`(20) + `nl-queries.jsonl`(20) + README | 无评测资产 |
| Eval 骨架 | `GoldenSetEvalTest` 规则层通过率 + `dvc.yaml` eval stage | 无通过率验证 |
| CI | `.github/workflows/ci.yml` 编译 + 单测 + 镜像构建验证 | 无流水线 |

---

## Day 0：开工前自检（5 分钟）

```bash
mvn test          # 期望 252 全绿（TDD 回归基线）
git status --short  # 工作树干净（仅非代码 untracked）
python --version && pip --version  # 装 DVC 前置
```

> ⚠️ 风险：全量 `mvn test` 含连库集成测，远程 MySQL 须可达；DVC 依赖 Python/pip，缺失需先装。

---

## 第一步：ADR-007 数据资产版本化（docs）

范围：新建 `docs/adr/ADR-007-data-asset-versioning-design.md`，沿用 ADR-004 模板。

- 背景：Prompt/Golden Set 是 AI 应用的数据资产，与代码同等重要；硬编码进代码则无法 Diff、无法回滚、无法复现。
- 决策：`prompts/`、`golden-set/` 用 DVC 版本化，remote 用本地目录起步，未来可切 MinIO/S3；Git 只提交 DVC 指针。
- 关键约束：数据内容不进 Git；每次变更必须 `dvc add` + commit 指针；`PromptRegistry` 从 DVC 目录加载，禁止硬编码。

检查点：完成后暂停，等待 review

---

## 第二步：DVC 初始化 + prompts/golden-set 入版本化（chore）

范围：`.dvc/`、`.dvcignore`、`prompts/`、`golden-set/`、`prompts.dvc`、`golden-set.dvc`、`.gitignore`。

```bash
pip install dvc
dvc init
mkdir -p data/dvc-storage && dvc remote add -d localstorage data/dvc-storage
mkdir -p prompts golden-set
printf '你是资金流水分类助手。根据商户名给出消费分类。\n商户：{{merchant}}\n' > prompts/classify-merchant.md
dvc add prompts golden-set
```

`.gitignore` 追加 `data/dvc-storage/`。Git 提交指针文件。`dvc status` / `dvc remote list` 验证。

> 依赖说明：装 `dvc` 是因行动指南 ADR-004 明确要求 DVC 版本化，Java 生态无等价工具，仅此一处引入 Python 依赖。

检查点：完成后暂停，等待 review

---

## 第三步：FileSystemPromptRegistry（TDD，纯单测）

范围：
- 新增：`src/main/java/com/finhub/datagov/FileSystemPromptRegistry.java`
- 新增测试：`src/test/java/com/finhub/datagov/FileSystemPromptRegistryTest.java`
- 禁止修改：`PromptRegistry` 接口、`NLTranslator`、领域层

**测试契约（先行）**：4 用例——按名加载 `.md`、`{{var}}` 变量替换、不存在抛 `IllegalArgumentException`、缺失变量占位符保留原样。用 `@TempDir` 造临时 prompts 目录，纯单测不连库（完整代码见执行计划 Task 3）。

**实现要求**：实现 `PromptRegistry`；模板文件名 = `<promptName>.md`；`loadPromptWithVariables` 做 `{{var}}` 替换，未提供变量的占位符保留原样。

检查点：完成后暂停，等待 review

---

## 第四步：Golden Set 数据资产（数据）

范围：`golden-set/classifier-cases.jsonl`（20 条，字段 `id`/`merchant`/`expectedCategory`）、`golden-set/nl-queries.jsonl`（20 条 NL 查询 + 错误模式）、`golden-set/README.md`（口径）。

> 说明：`classifier-cases.jsonl` 的 20 条需对照 `TransactionClassifierImpl` 规则表与 `Category` 枚举逐条核对，确保 `expectedCategory` 是规则真实输出或合理边界（含应落 UNCLASSIFIED 的边界词）。DGG 可提供真实账单商户名提升真实性。填完 `dvc add golden-set` 更新指针。

检查点：完成后暂停，等待 review

---

## 第五步：Golden Set Eval 骨架（TDD，规则层跑通）

范围：
- 新增测试：`src/test/java/com/finhub/datagov/GoldenSetEvalTest.java`
- 新增：`dvc.yaml`（eval stage）

**测试契约（先行）**：`shouldMeetClassifierPassRate` 读 `classifier-cases.jsonl`，对 `new TransactionClassifierImpl()` 逐条断言 `suggest(merchant).category() == expected`，`total>=20` 且通过率 `>=0.9`；NL 用例 `@Disabled("TODO Day8+: LLM 接通后跑 NL 评测")`。评测逻辑放测试内（MVP），不新建 main 侧 Eval 类。

**dvc.yaml**：

```yaml
stages:
  eval:
    cmd: mvn -q test -Dtest=GoldenSetEvalTest
    deps:
      - golden-set/classifier-cases.jsonl
      - src/main/java/com/finhub/fundflow/domain/service/TransactionClassifierImpl.java
```

`dvc repro` 验证可复现。

> 不达标时修 `classifier-cases.jsonl`（用例与规则对齐），而非放松断言。

检查点：完成后暂停，等待 review

---

## 第六步：GitHub Actions CI（ci）

范围：新建 `.github/workflows/ci.yml`。三个 job：

- `build`：Temurin 17 + `mvn -B -DskipTests package`（编译 + 打包）。
- `unit-test`：纯单测口径（排除连库集成测：E2E/RepositoryImpl/事件监听器等），上传 JaCoCo 产物（复用已配 jacoco）。
- `docker`：`docker/build-push-action` 用现有 `Dockerfile` 构建验证（`push: false`，不进 registry），`needs: [build, unit-test]`。

> 单测排除清单以 `src/test` 实际连库类名为准；更稳的做法是给连库类统一 `@Tag("integration")` 后用 `-DexcludedGroups=integration`（可作为本步的小重构，需 TDD 回归）。

检查点：完成后暂停，等待 review

---

## 第七步：EOD 收尾

- `mvn test` 全量回归闸门（本地，含连库集成测，252 + 新增 全绿）。
- `dvc repro` 验证 eval pipeline 可复现。
- `ROADMAP.md` Day 7 标完成 + 最近验证表 + 待办勾选 + 阻塞表更新。
- `README.md` 补 Day 7 进度 + 数据资产管理（DVC）说明。

---

## 今日检查清单（Day 7 EOD）

**TDD 硬约束验收**：
- [ ] Task3/Task5 都先写测试契约并看到 RED（实现缺失，非编译错误）
- [ ] GREEN 后改动测试契约 PASS，全量 `mvn test` 旧测试无一回归

**功能交付**：
- [ ] ADR-007 落地
- [ ] DVC init + 本地 remote + `prompts/`/`golden-set/` 入版本化
- [ ] `FileSystemPromptRegistry` 实现并测试通过
- [ ] Golden Set 20+20 条入库 + README 口径
- [ ] `GoldenSetEvalTest` 规则层通过率达标 + `dvc.yaml` eval stage `dvc repro` 可复现
- [ ] CI 三 job 绿（编译 + 单测 + 镜像构建验证）

**收尾**：
- [ ] `mvn test` 全绿（退出码 0）
- [ ] ROADMAP/README 更新
- [ ] 已知缺口更新：NL 评测、remote 切云存储、`spring-ai` 接通 待 Day8+

---

## 下一步预告（Day 8）

| 任务 | 内容 |
| ---- | ---- |
| 安全加固 | 字段加密验证（DB counterparty/remark 密文）、日志脱敏全局过滤器、临时文件清理 |
| 容器化收尾 | MySQL 数据卷备份、镜像优化 < 100MB |
| 查询上下文（顺延） | `QueryAppService` + `QueryRouter` 责任链 |
| MCP Tool（顺延） | `McpToolDispatcher` 实现类 |
| `anomaly_reason_code` 列 | 建列 + Converter 落库（替换哨兵占位） |
| 通知语义 | 监听器改 `@TransactionalEventListener(AFTER_COMMIT)` |
| DVC 升级 | remote 切 MinIO/S3；NL Eval 接通（`spring-ai`） |
