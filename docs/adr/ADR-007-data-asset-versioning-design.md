# ADR-007: 数据资产版本化 — DVC 方案

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-07-30 |
| **决策者** | xiaod |
| **影响范围** | 数据治理上下文（datagov） |

## 背景

FinHub 是「个人资金数据治理中台」，核心能力是把 CSV（支付宝/微信账单）导入、清洗、分类、存库。

随着 AI 能力接入（`TransactionClassifier` 规则层 + 未来的 LLM NL2SQL），项目会产生两类数据资产：

1. **Prompt 模板**：引导 LLM 分类/解释的自然语言指令（如 `prompts/classify-merchant.md`）
2. **Golden Set**：标准答案测试集，用于量化评测分类准确率（如 `golden-set/classifier-cases.jsonl`）

这些数据和代码同等重要——代码改了 Prompt、分类规则变了，Golden Set 的通过率会随之波动。需要像管代码一样管这些数据资产。

**现状问题**：
- Prompt 可能散落在代码里（硬编码），无法追踪修改历史
- Golden Set 尚未建立，无从量化评测分类效果
- 数据资产没有版本化，无法「回滚到上一个版本的 Prompt」

## 决策

**Prompt 模板（`prompts/`）与 Golden Set（`golden-set/`）用 DVC（Data Version Control）版本化。**

- 本地 Remote 起步（`data/dvc-storage/`），未来可切换 MinIO / S3 / 阿里云 OSS
- Git 只提交 DVC 指针文件（`.dvc/`、`*.dvc`、`dvc.yaml`），不提交真实数据内容
- `PromptRegistry` 从 DVC 管理的目录动态加载，禁止硬编码 Prompt

## 实现

```
FinHub/
├── .dvc/                    # DVC 元数据（git 管理）
├── .dvcignore               # DVC 忽略规则
├── dvc.yaml                 # DVC Pipeline 定义
├── prompts/                 # Prompt 模板（DVC 管理）
│   └── classify-merchant.md
├── prompts.dvc              # Prompt 指针（git 管理）
├── golden-set/              # Golden Set 数据（DVC 管理）
│   ├── classifier-cases.jsonl
│   └── nl-queries.jsonl
├── golden-set.dvc           # Golden Set 指针（git 管理）
└── data/
    └── dvc-storage/         # 本地 Remote（gitignore，不提交）
```

### 关键操作

| 操作 | 命令 |
|------|------|
| 新增/修改数据 | `dvc add prompts` 或 `dvc add golden-set` → Git commit 指针文件 |
| 切换版本 | `git checkout <commit>` → `dvc checkout` |
| 查看变更 | `dvc diff` |
| 推送到 Remote | `dvc push` |
| 从 Remote 拉取 | `dvc pull` |
| 复现评测 | `dvc repro`（定义在 `dvc.yaml`） |

## 关键约束

1. **数据内容不进 Git**：真实数据只存在于 `prompts/`、`golden-set/` 目录，由 DVC 管理。Git 只管指针。
2. **每次变更必须 commit**：Prompt / Golden Set 修改后，必须 `dvc add` + `git commit` 指针，保证 Git 历史可追溯。
3. **Prompt 必须动态加载**：`PromptRegistry` 从文件系统加载模板，禁止在代码里硬编码 Prompt 字符串。
4. **Remote 可切换**：当前用本地目录（`data/dvc-storage/`），未来可迁移到 MinIO/S3，只需改 `dvc remote` 配置，不改代码。
5. **依赖最小化**：仅在需要版本化大数据（Prompt 集合、Golden Set）时用 DVC；小文件（如配置文件）仍用 Git。

## 后果

- **可追溯**：每次 Prompt / Golden Set 修改都有 Git commit 记录，可 Diff、可回滚
- **可复现**：通过 `dvc checkout` 可精确还原历史版本的数据，配合 `dvc repro` 可复现历史评测结果
- **团队协作**：Remote 共享后，团队成员可 `dvc pull` 拿到统一的 Prompt 和 Golden Set
- **引入跨生态依赖**：DVC 是 Python 工具链，需 `pip install dvc`；这是 Java 项目唯一引入的 Python 依赖
- **Remote 切换成本**：未来从本地切换到云存储需重新配置，但不涉及代码改动
