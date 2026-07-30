---
name: jacoco-report
description: Use when 跑完单元测试后需要查看代码覆盖率报告，或需要对照 DDD 分层覆盖率目标检查测试缺口。适用于已配 jacoco-maven-plugin 的 Java/Maven 项目。
---

# JaCoCo 覆盖率报告

## 概述
跑 `mvn verify` 生成 JaCoCo 报告，用本 skill 自带脚本解析 `jacoco.xml`，输出覆盖率汇总 + DDD 分层差距 + 未覆盖热点。

## 何时使用
- 跑完单元测试后要查看覆盖率
- 检查某层 / 某包是否达标
- 提交前确认测试覆盖充分

不适用：项目未配 jacoco-maven-plugin（需先配 pom）。

## 前提
项目 pom 已配 `jacoco-maven-plugin`：prepare-agent 注入 agent，report 绑 verify 生成 `target/site/jacoco/index.html` + `jacoco.xml`。surefire argLine 用 `@{argLine}` 与 JaCoCo agent 共存。

## 执行步骤
1. 项目根目录跑 `mvn verify`（report 绑 verify；`mvn test` 不生成报告）。
2. 确认 `target/site/jacoco/jacoco.xml` 存在。
3. 运行自带脚本：
   ```
   python .claude/skills/jacoco-report/parse_jacoco.py
   ```
   脚本输出四部分：总览、按包 LINE 覆盖率、DDD 层差距、未覆盖类 Top 10。
4. 按「输出契约」模板填充脚本数据，原样输出给用户。

## 输出契约（严格按此模板照填，不增不减章节）

跑完脚本后，原样输出以下结构（数据全部来自脚本，不编造）：

> ## 覆盖率报告（JaCoCo）
>
> **总览**：LINE {x}% / BRANCH {x}% / METHOD {x}% / CLASS {x}%
>
> **DDD 分层达标**
> | 层 | LINE | 目标 | 状态 |
> |---|---|---|---|
> | domain | {x}% | 100% | ✗/✓ |
> | application | {x}% | 80% | ✗/✓ |
> | acl | {x}% | 80% | ✗/✓ |
> | acl-adapter | {x}% | 80% | ✗/✓ |
> | infrastructure | {x}% | 70% | ✗/✓ |
> | ai | {x}% | 100% | ✗/✓ |
> | query | {x}% | 80% | ✗/✓ |
> | knowledge | {x}% | 80% | ✗/✓ |
>
> **未覆盖热点**：{class}(missed=N) / {class}(missed=N) / ...（Top 3-5）
>
> **建议**：{1-3 条可执行建议，结合 BRANCH 偏低 / 未达标层 / 未覆盖热点}
>
> HTML 明细：target/site/jacoco/index.html

填充规则：
- 状态：达标 ✓，未达标 ✗；覆盖率为 0% 的层标注「（未实现）」。
- 建议必须指向具体类 / 层，不写空话。

## 脚本说明
`parse_jacoco.py` 解析 `jacoco.xml` 的 `<counter>`，按包聚合 LINE 覆盖率，并按包名前缀映射到 DDD 层对照目标。输出用英文标签（规避 Windows GBK 终端中文乱码）。可选参数指定 `jacoco.xml` 路径，默认 `target/site/jacoco/jacoco.xml`。

## DDD 分层覆盖率目标（脚本内置）
| 层 | 目标 |
|---|---|
| 领域层（domain） | 100% |
| 应用层（application） | >80% |
| ACL 防腐层（acl / adapter） | >80% |
| 基础设施层（infrastructure） | >70% |
| AI 校验层 | 100% |

## 常见问题
- **报告没生成**：用了 `mvn test` 而非 `mvn verify`（report 绑 verify）。
- **覆盖率全 0**：prepare-agent 未生效，检查 surefire argLine 是否用 `@{argLine}` 而非写死覆盖了 JaCoCo 注入。
- **脚本输出乱码**：脚本已用英文标签规避；若仍乱码，设 `PYTHONIOENCODING=utf-8` 再跑。
- **中文日志乱码**：argLine 需含 `-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8`；若 surefire ForkNode 接收端仍乱，需 `.mvn/jvm.config` 设 Maven 进程 UTF-8。
