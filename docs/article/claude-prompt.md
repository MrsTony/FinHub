以下是可直接**一次性全选复制**给 Claude 的完整对话脚本。包含角色指令、文章初稿素材、插图标注、输出格式要求。

---

```
你是一位资深技术公众号编辑，擅长将硬核架构内容转化为"有干货、有节奏、有记忆点"的微信公众号长文。目标读者是 3-5 年经验的后端工程师，正在用 AI 辅助编程但项目越来越乱。

我需要你基于以下素材，输出一篇可直接复制到公众号编辑器（Markdown 格式）的终稿。

## 一、核心素材：文章初稿

# 当 AI 成为架构腐败的加速器：我的 FinHub DDD 防御实录

> 不是 AI 写不好代码，是你的管控体系没跟上。分享一套让 Claude 在限定沙盒内工作的 AI Harness 工程化方案。

## 一、问题：AI 正在加速架构腐败

用 Claude/Copilot 写代码三个月后，我发现一个反直觉的事实：

**AI 不是帮你减少技术债，它是技术债的批量制造机。**

### 1.1 典型症状

| 症状 | 根因 | 后果 |
|------|------|------|
| 核心域被 `@Service` 污染 | AI 觉得"加个注解方便注入" | 领域模型与 Spring 耦合，单元测试必须启动容器 |
| 聚合根长出 `public setter` | AI 觉得"方便后续修改" | 业务不变量被绕过，脏数据直接入库 |
| 测试预期被"贴心调整" | AI 认为"测试应该匹配实现" | 防御性测试失效，异常场景被掩盖 |
| 47 个文件一次变更 | 口头指令无范围锁定 | 架构边界击穿，回滚成本指数级上升 |

### 1.2 为什么传统 Prompt 工程不够？

现在的 AI 编程教程都在教你：
- "怎么写更好的 Prompt"
- "怎么让 AI 理解你的需求"
- "怎么让 AI 一次生成更多代码"

**但软件工程的核心矛盾不是"生成速度"，而是"可控性"。**

当你说"帮我实现导入功能"时，AI 的优化目标是**完成任务**，不是**守护你的架构**。它不理解：
- 为什么 `Money` 必须是值对象而不是 `double`
- 为什么 `Transaction` 不能有 `public setter`
- 为什么 `fundflow.domain` 包不能出现 `@Autowired`

**AI 是能力极强的实习生，但架构决策必须是人类专属。**

## 二、解法：AI Harness 三层管控体系

我基于 FinHub 项目（个人资金数据治理中台，DDD + MyBatis-Plus + 本地 LLM）提炼了一套 **AI Harness** 工程化方案。

核心思想：**人类定义契约（接口 + 测试 + 模板），AI 在沙盒内填充实现，Git 流水线审计每一行变更。**

```
┌─────────────────────────────────────────┐
│  第一层：宪法（Constitution）            │
│  文件：CLAUDE_CODE.md                   │
│  内容：六条绝对禁令，AI 无权逾越          │
│  更新频率：架构变更时                    │
├─────────────────────────────────────────┤
│  第二层：法律（Law / Templates）         │
│  文件：task-templates.md                │
│  内容：五种场景任务模板，精确到文件路径     │
│  更新频率：新场景出现时                   │
├─────────────────────────────────────────┤
│  第三层：审计（Audit / Pipeline）        │
│  工具：Git + Review 清单                │
│  内容：范围审查 → 逻辑审查 → 选择性提交   │
│  更新频率：每次任务                      │
└─────────────────────────────────────────┘
```

## 三、第一层：宪法——六条架构禁令

写在 `CLAUDE_CODE.md` 开头，违反任何一条立即回滚，不讨论。

### 禁令 1：禁止修改架构边界
- 禁止新增 / 删除 / 重命名包
- 禁止修改接口方法签名（参数、返回值、异常）
- 禁止移动类到不同限界上下文

**为什么？** 架构边界是人类的战略决策。AI 觉得"把 `Money` 移到 `common` 包更方便复用"，但它不懂这是资金流水核心域的值对象。

### 禁令 2：禁止污染核心域
- `fundflow.domain`、`knowledge.domain` 包内禁止出现 `@Service`、`@Autowired`、`@Transactional`
- 领域层禁止调用 `Repository`、`HttpClient`、`Cache`

**为什么？** DDD 的核心域必须纯粹。AI 给 `Money` 加 `@Data` 和 `@Table`，值对象就变成了 JPA 实体，不可变性被破坏。

### 禁令 3：禁止绕过不变量
- 聚合根禁止 `public setter`、无参构造器
- 禁止绕过工厂方法直接 `new Transaction()`

**为什么？** 业务规则必须封装在聚合根内部。AI 生成的 `public setCategory()` 让外部代码可以任意修改分类，破坏了"分类必须与资金流向兼容"的不变量。

### 禁令 4：禁止擅自引入依赖
- 禁止修改 `pom.xml`
- 禁止修改 `application.yml`、`docker-compose.yml`

**为什么？** 技术选型是架构决策。AI 推荐 Lombok、MapStruct、H2 数据库，会膨胀技术栈、破坏团队统一性。

### 禁令 5：禁止修改测试契约
- 禁止修改 `src/test` 下任何文件
- 禁止删除 / 重命名测试方法

**为什么？** 测试是契约，不是实现的对齐工具。AI 把 `shouldRejectNegativeAmount` 的预期从 `IllegalArgumentException` 改成 `RuntimeException`，等于主动掩盖 Bug。

### 禁令 6：禁止跨上下文越界
- `fundflow` 禁止直接引用 `knowledge` 的类
- 必须走防腐层（ACL）接口或领域事件

**为什么？** 限界上下文间的依赖必须通过防腐层隔离。直接引用会导致大泥球架构。

## 四、第二层：法律——五种场景模板

口头指令 = 架构自杀。每次任务必须基于模板，精确到文件路径。

### 模板结构（五段式）

```markdown
/task: [明确动作] [具体文件]

范围：
- 仅限文件：[完整路径]
- 禁止修改：[明确列出不能碰的东西]

要求：
1. [技术约束]
2. [业务规则]
3. [安全规则]
...
N. 完成后执行：mvn test -Dtest=[精确测试类]

检查点：完成后暂停，等待我 review
```

### 模板 A：值对象填充

**适用场景**：`fundflow.domain.vo` 包内值对象，方法体为 `// TODO`

```markdown
/task: 实现 Money.java 中所有 TODO 的方法

范围：
- 仅限文件：src/main/java/com/example/finhub/fundflow/domain/vo/Money.java
- 禁止修改：方法签名、类名、包名、字段定义、import 语句

要求：
1. 使用 Java 17 Record 语法，保持不可变性（返回新实例）
2. 所有校验在构造器/方法内完成
3. 异常使用 IllegalArgumentException（业务非法）、NullPointerException（空值）
4. 精度强制：setScale(2, RoundingMode.HALF_UP)
5. 加法/比较必须校验币种一致性
6. toString 脱敏：金额替换为 "***"，保留币种
7. 完成后执行：mvn test -Dtest=MoneyTest
8. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

**效果对比**：

| 方式 | 指令字数 | AI 变更文件数 | 审查时间 |
|------|---------|-------------|---------|
| 口头 | 10 字 | 47 个 | 2 小时 |
| 模板 | 200 字 | 1 个 | 10 分钟 |

## 五、第三层：审计——Git 流水线

AI 交卷后，执行三步审计。这是最后的防线。

### 5.1 范围审查：先看数量

```bash
git diff --stat
```

**预期**：1 个文件（符合模板范围）。  
**实际**：3 个文件 → 立刻警觉，找出越界文件。

### 5.2 逻辑审查：再看质量

```bash
git diff src/main/java/.../fundflow/domain/vo/Money.java
```

**检查点**：
- 方法签名没变？
- 无 `@Autowired` 溜进 `domain` 包？
- 无 `System.out.println`？
- 无敏感字段明文打印？

### 5.3 选择性添加：逐块审查

```bash
git add -p
```

**关键**：逐块输入 `y`（接受）或 `n`（拒绝）。  
全量 `git add .` 等于放弃审查。

### 5.4 带审计信息的提交

```bash
git commit -m "feat(fundflow/domain): implement Money value object

- 精度强制 2 位小数
- 加法/比较币种一致性校验
- toString 脱敏

Claude-Task: A-001
Reviewed-By: [你的名字]"
```

## 六、实战：FinHub 的 DDD 防御案例

### 6.1 场景：Money 值对象实现

**架构决策（ADR-001）**：
- 金额精度绝对化，禁止 `float/double`
- `BigDecimal` + `DECIMAL(18,2)`，值对象不可变

**人类先写测试契约**（22 个测试方法）：

```java
@Test
void shouldReturnNewInstanceWhenAdding() {
    Money m1 = new Money(new BigDecimal("100.00"), "CNY");
    Money m2 = new Money(new BigDecimal("50.50"), "CNY");
    Money result = m1.add(m2);
    
    assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("150.50"));
    assertThat(result).isNotSameAs(m1); // 不可变性校验
}

@Test
void shouldNotExposeAmountInToString() {
    Money money = new Money(new BigDecimal("99999.99"), "CNY");
    assertThat(money.toString()).doesNotContain("99999.99");
}
```

**人类下发模板指令**（模板 A，范围锁定 `Money.java`）。

**AI 填充实现**：60 行代码，仅修改 1 个文件。

**人类审计**：
- `git diff --stat`：1 个文件 ✓
- `git diff`：无 Spring 注解 ✓，无新增 import ✓
- `mvn test -Dtest=MoneyTest`：22/22 通过 ✓

**总耗时**：10 分钟。

### 6.2 架构收益：聚合根不变量守护

```java
public class Transaction {
    private Category category; // 无 public setter
    
    public void markClassified(Category newCategory, String source) {
        // 聚合根自己校验业务规则
        if (!newCategory.isCompatibleWith(this.direction)) {
            throw new IllegalArgumentException(
                "分类 " + newCategory + " 与方向 " + this.direction + " 不兼容"
            );
        }
        this.category = newCategory;
        registerEvent(new TransactionClassifiedEvent(this.id, newCategory, source));
    }
}
```

**关键**：分类修改权在聚合根，AI 的 `CategorySuggestionEngine`（防腐层）只能给建议，不能替聚合根决策。

## 七、认知升级：工程师能力模型转变

| 阶段 | 人类角色 | AI 角色 | 核心能力 |
|------|---------|--------|---------|
| **放任** | 旁观者 | 全栈工程师 | 无，架构失控 |
| **审查** | 纠错员 | 初级开发者 | 代码审查能力 |
| **模板化** | 架构师 | 精确执行者 | **架构决策 + 契约设计** |
| **契约化** | 契约设计者 | 契约履行者 | TDD 先行，测试即契约 |

**核心观点**：

> AI 能写代码，但**"什么代码放在哪个包"**是人类的业务理解。  
> AI 能跑测试，但**"测试应该防御什么场景"**是人类的领域知识。  
> AI 能加依赖，但**"这个技术栈 3 年后是否维护"**是人类的架构判断。

**AI 时代工程师的核心竞争力 = 架构决策能力 + AI 协作管控能力。**

## 八、行动清单：今晚就能落地

1. **写 3 条禁令**：新建 `CLAUDE_CODE.md`，定义你的绝对红线
2. **写 1 个模板**：选最简单的值对象，按五段式模板下发任务
3. **用 `git add -p`**：逐块审查 AI 产出，拒绝可疑代码

## 九、写在最后

AI 编程工具的普及，正在加速两极分化：

- **不会管控的人**：AI 写得越快，架构腐烂越快，3 个月后项目不可维护
- **会管控的人**：AI 成为精确执行者，人类专注于架构设计和业务建模

**FinHub 项目的完整模板库、审查清单和 DDD 包结构已开源。**

关注后回复 **AIHarness**，获取：
- `CLAUDE_CODE.md` 完整禁令模板
- 五种场景任务模板（Markdown 格式）
- Git 审计 Review 清单

---

**作者**：XXX  
**项目**：FinHub 个人资金数据治理中台（DDD + MyBatis-Plus + 本地 LLM）  
**标签**：#AI编程 #DDD #软件架构 #Claude #工程化

## 二、插图标注（我已生成 3 张 Excalidraw 图）

请在文章中以下位置插入图片占位符，我会替换为实际图片：

<!-- 图1：AI Harness 三层架构图 -->
<!-- 建议位置：Part 2 "解法" 章节开头，替代 ASCII 流程图 -->
<!-- 描述：深色背景金字塔，宪法(红)→法律(蓝)→审计(绿)，右侧标注"什么绝对不能做/怎么做/做了对不对" -->

<!-- 图2：口头指令 vs 模板指令对比图 -->
<!-- 建议位置：Part 4 "模板" 章节开头，替代效果对比表格 -->
<!-- 描述：左右对比卡片，左侧灰色"❌ 口头指令"（47个文件/2小时回滚），右侧绿色"✅ 模板指令"（1个文件/10分钟通过） -->

<!-- 图3：Git 审计三步流程图 -->
<!-- 建议位置：Part 5 "审计" 章节开头 -->
<!-- 描述：三个蓝色矩形横向排列（git diff --stat / git diff / git add -p），箭头连接，底部红色警告框"git add . = 放弃审查" -->

## 三、输出要求

### 1. 标题
提供 3 个选项：
- 选项A（痛点型）：用 Claude 写代码 3 个月，我的项目变成了屎山：从失控到驾驭的 AI 工程化实践
- 选项B（方法论型）：AI Harness：我如何用"三层管控"让 Claude 成为合格实习生
- 选项C（反常识型）：别再用"帮我写个功能"指挥 AI 了：FinHub 项目的精确制导实践

我选后你展开全文。

### 2. 排版优化（微信公众号适配）
- **重点句**：单独成段，加粗，前后空行。每 800 字至少 1 个"金句框"
- **对比数据**：独立成行，加粗。如 `47 个文件 → 1 个文件`
- **代码块**：只保留核心 5-8 行，超出用"..."省略。代码块上方加场景说明小标题
- **列表项**：用"❶ ❷ ❸"或"▎"替代普通数字，增强视觉节奏
- **段落长度**：手机端每段不超过 4 行，长段落必须拆分

### 3. 语气调整
- 增加工程师黑话 + 自嘲（如"凌晨 2 点回滚代码的我，像极了第一次带实习生的 mentor"）
- 减少纯理论说教，增加"我当时怎么想的"第一人称视角
- 每个技术点后跟一句"为什么这很重要"的解释（给非 DDD 专家读者）

### 4. 禁止事项
- 不要修改技术细节（六条禁令、三层体系、Money 案例逻辑必须保留）
- 不要新增不存在的概念（如不要引入"Prompt Engineering"等无关内容）
- 不要输出 HTML，只输出 Markdown
- 不要修改测试契约相关描述（这是文章核心卖点之一）

### 5. 输出格式
先输出 3 个标题选项，等我确认后输出完整文章。
```

---

**使用方法**：
1. 复制上方全部内容（从 `你是一位资深...` 到最后）
2. 粘贴到 Claude 对话框
3. Claude 会输出 3 个标题选项
4. 你回复选一个（如"选 B"）
5. Claude 输出完整终稿，你直接复制到公众号编辑器

需要我把这个脚本保存为 `docs/article/claude-prompt.md` 方便你下次复用吗？