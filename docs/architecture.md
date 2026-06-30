# GanttLens 架构草案

## 1. 项目命名

### 1.1 名称

**GanttLens**

- "Gantt" = 甘特图，明确产品领域
- "Lens" = 透镜/洞察，表达"分析、洞察排期数据"的定位

### 1.2 命名由来

| 考虑因素 | 说明 |
|----------|------|
| **不包含 "PlantUML"** | PlantUML 是注册商标，使用可能产生侵权风险 |
| **不包含 "Puml"** | "Puml" 是 PlantUML 的缩写，同样有商标风险 |
| **简短好记** | 两个音节，便于传播 |
| **域名友好** | ganttlens.com 等域名可用 |
| **GitHub 友好** | github.com/xxx/ganttlens 搜索友好 |

### 1.3 声明方式

在文档和 README 中使用以下声明（而非暗示官方授权）：

> GanttLens is an independent analysis tool that supports a subset of PlantUML Gantt syntax.
> GanttLens is not affiliated with or endorsed by PlantUML.

### 1.4 Maven 坐标

```xml
<groupId>com.ganttlens</groupId>
<artifactId>ganttlens</artifactId>
```

### 1.5 Java 包名

```
com.ganttlens.core      # 核心解析 + 分析
com.ganttlens.cli       # CLI 入口
com.ganttlens.gui       # JavaFX GUI (Phase 3)
```

### 1.6 CLI 命令名

```
ganttlens <command> [options]
```

### 1.7 候选名称对比

| 名称 | 风险 | 评价 |
|------|------|------|
| **GanttLens** | ✅ 无风险 | 推荐，含义精准，无商标问题 |
| GanttInsight | ✅ 无风险 | 备选，含义类似 |
| PumlGantt | ⚠️ 含 "Puml" | 有商标风险，避免 |
| PlantUML Gantt Analyzer | 🔴 含 "PlantUML" | 商标侵权风险，避免 |

---

## 2. 项目定位

**GanttLens** 是 PlantUML 甘特图的分析增强工具，解析 `.puml` 文件中的排期信息，提供人力统计、负载分析、风险检测等功能。

核心理念：**用 PlantUML 写排期，用 GanttLens 看人力。**

### 2.2 目标用户

- 技术负责人
- 项目负责人
- 研发组长
- 交付经理
- 喜欢文档即代码工作流的工程团队

### 2.3 差异化价值

相比单纯 PlantUML：

- 不只画图，还回答人力和风险问题
- 支持人员视图和统计图表
- 支持导出报表

相比传统项目管理软件（Jira、飞书项目、MS Project）：

- 文本源文件可版本化，适合 Git Review
- 更轻量，适合工程文档体系

相比 Mermaid Gantt：

- PlantUML Gantt 的资源语法更适合人力统计
- 可以利用 `{人员:百分比}` 这类表达做人天计算

---

## 3. 演进路线

```
Phase 1 (MVP)          Phase 2 (CLI 完善)        Phase 3 (GUI)
─────────────         ──────────────────        ─────────────
CLI 工具               CLI 功能完善               JavaFX 跨平台 GUI
· 解析核心语法          · 更多 PlantUML 语法       · 可视化甘特图
· 人天统计             · 关键路径分析             · 交互式人员视图
· 超载检测             · 报表导出 (CSV/MD/HTML)   · 实时负载热力图
· 基本文本输出         · 节假日配置               · 风险面板
                                            · 报表导出
```

### 3.2 产品形态

| 形态 | 阶段 | 说明 |
|------|------|------|
| CLI 工具 | Phase 1-2 | 适合 CI、文档自动化、周报自动生成 |
| GUI 桌面应用 | Phase 3 | JavaFX 跨平台，交互式甘特图和热力图 |
| 本地 Web 工具 | 未来 | 前端 Vue/React + ECharts，拖拽 .puml 文件 |
| VS Code 插件 | 未来 | 编辑 .puml 时右侧展示统计，保存时检查风险 |

---

## 4. 技术栈

| 层次 | 技术 | 版本要求 | 说明 |
|------|------|---------|------|
| 语言 | Java | 17+ | LTS 版本，支持 records、text blocks |
| 语法解析 | ANTLR4 | 4.13+ | DSL 解析核心 |
| CLI 框架 | picocli | 4.7+ | 注解式 CLI，支持子命令 |
| GUI 框架 | JavaFX | 21+ | Phase 3 使用 |
| 构建工具 | Maven | 3.9+ | 多模块管理 |
| 打包 | GraalVM Native Image | 可选 | 提升启动速度 |
| 测试 | JUnit 5 | 5.10+ | 单元测试 |
| 测试 | AssertJ | 3.25+ | 断言增强 |

---

## 5. 模块结构

```
GanttLens/
├── pom.xml                              # 父 POM
│
├── core/                                # 核心模块 - 解析 + 分析
│   ├── pom.xml
│   └── src/main/
│       ├── antlr4/
│       │   └── PlantUMLGantt.g4        # ANTLR4 语法定义
│       ├── java/com/ganttlens/
│       │   ├── model/                   # 数据模型
│       │   │   ├── Task.java
│       │   │   ├── Assignment.java
│       │   │   ├── PersonDailyLoad.java
│       │   │   ├── GanttSchedule.java
│       │   │   └── ProjectStats.java
│       │   ├── parser/                  # 解析层
│       │   │   ├── GanttFileParser.java
│       │   │   └── GanttParseListener.java
│       │   ├── analyzer/                # 分析层
│       │   │   ├── WorkloadAnalyzer.java
│       │   │   ├── OverloadChecker.java
│       │   │   ├── RiskDetector.java
│       │   │   └── CriticalPathAnalyzer.java
│       │   ├── export/                  # 导出层
│       │   │   ├── CsvExporter.java
│       │   │   ├── MarkdownExporter.java
│       │   │   └── HtmlExporter.java
│       │   └── config/                  # 配置
│       │       ├── WorkCalendar.java
│       │       └── AnalysisConfig.java
│       └── resources/
│           └── PlantUMLGanttVisitor.java  # ANTLR4 生成代码
│
├── cli/                                 # CLI 模块
│   ├── pom.xml
│   └── src/main/java/com/ganttlens/cli/
│       ├── CliApp.java                  # 主入口
│       ├── AnalyzeCommand.java          # analyze 子命令
│       ├── CheckCommand.java            # check 子命令
│       └── ExportCommand.java           # export 子命令
│
├── gui/                                 # GUI 模块 (Phase 3)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ganttlens/gui/
│       │   ├── GuiApp.java
│       │   ├── controller/
│       │   │   ├── GanttViewController.java
│       │   │   ├── WorkloadViewController.java
│       │   │   └── StatsViewController.java
│       │   ├── view/
│       │   │   ├── GanttView.java
│       │   │   ├── WorkloadHeatmap.java
│       │   │   └── StatsPanel.java
│       │   └── model/
│       │       └── UiTask.java
│       └── resources/
│           ├── fxml/
│           │   ├── gantt-view.fxml
│           │   ├── workload-view.fxml
│           │   └── stats-panel.fxml
│           └── css/
│               └── styles.css
│
└── dist/                                # 打包分发
    ├── build-native.sh                  # GraalVM 打包脚本
    └── jpackage-config/                 # jpackage 配置
```

---

## 6. ANTLR4 语法设计 (PlantUMLGantt.g4)

### 6.1 设计原则

- **只支持明确子集**，不支持的语法给出 warning 而非静默忽略
- 语法设计尽量贴近 PlantUML 原始写法，减少用户学习成本
- 对自由度高的部分（如任务名称）做合理约束

### 6.2 语法草案

```antlr
grammar PlantUMLGantt;

// ========== 入口 ==========
ganttFile
    : STARTGANTT directive* task* ENDGANTT
    ;

// ========== 指令 ==========
directive
    : weekendsDirective
    | holidayDirective
    | personOffDirective
    | printscaleDirective
    | titleDirective
    ;

weekendsDirective : 'saturday are closed' | 'sunday are closed' ;
holidayDirective  : 'YYYY-MM-DD' 'is closed' ;  // 简化
personOffDirective: '{' personName '}' 'is off on' DATE ;
printscaleDirective: 'printscale' ('weekly' | 'daily' | 'monthly') ;
titleDirective    : 'title' TEXT ;

// ========== 任务 ==========
task
    : taskGroup?
      '[' taskName ']'
      ('on' resourceList)?
      ('requires' duration | 'starts at' startDate)
      ('and ends at' endDate)?
      (''s end' | ' starts at [' TEXT ']'s end')?
    ;

taskGroup : '--' TEXT '--' ;

resourceList
    : resource (',' resource)*
    ;

resource
    : '{' personName (' ':' ratio ')'}'
    ;

personName : WORD+ ;
ratio      : INTEGER '%' ;
duration   : INTEGER 'days' | 'hours' ;
startDate  : DATE | taskRef ('start' | 'end') ;
taskRef    : '[' TEXT ']' ;
endDate    : DATE ;

// ========== 词法 ==========
STARTGANTT : '@startgantt' ;
ENDGANTT   : '@endgantt' ;
DATE       : DIGIT DIGIT DIGIT DIGIT '-' DIGIT DIGIT '-' DIGIT DIGIT ;
INTEGER    : [0-9]+ ;
WORD       : ~[{}\[\]\s]+ ;
TEXT       : ~[]+? ;
WS         : [ \t\r\n]+ -> skip ;
```

> **注意**：以上为草案，实际实现时需要根据 PlantUML 真实语法行为做调整和测试。

---

## 7. 核心数据模型

```java
// 任务
public record Task(
    String id,                    // 自动生成
    String name,                  // 任务名称
    String group,                 // 所属阶段（可选）
    LocalDate startDate,          // 计算后的开始日期
    LocalDate endDate,            // 计算后的结束日期
    int durationDays,             // 持续天数（工作日）
    List<Assignment> assignments, // 人员分配
    List<String> dependencyIds,   // 依赖任务 ID
    TaskStatus status             // 状态
) {}

// 人员分配
public record Assignment(
    String person,                // 人员名称
    double ratio                  // 投入比例 0.0-1.0
) {}

// 每日负载
public record PersonDailyLoad(
    String person,
    LocalDate date,
    double totalLoad,             // 当日总投入比例
    List<TaskLoad> tasks          // 参与的任务及各自比例
) {}

public record TaskLoad(
    String taskId,
    String taskName,
    double ratio
) {}

// 项目统计
public record ProjectStats(
    int totalManDays,
    Map<String, Integer> personManDays,       // 每人总人天
    Map<String, List<PersonDailyLoad>> personDailyLoads, // 每人每日负载
    List<OverloadRecord> overloads,           // 超载记录
    List<RiskItem> risks                      // 风险项
) {}

// 超载记录
public record OverloadRecord(
    String person,
    LocalDate date,
    double totalLoad,
    List<TaskLoad> tasks
) {}

// 风险项
public record RiskItem(
    RiskType type,          // UNASSIGNED, NO_DATE, OVERLOAD, CIRCULAR_DEP, WEEKEND_TASK
    String description,
    String taskId           // 关联任务（可选）
) {}
```

---

## 8. 分析引擎设计

### 8.1 解析流程

```
.puml 文件
    │
    ▼
ANTLR4 Lexer/Parser  →  Parse Tree
    │
    ▼
GanttParseListener   →  领域模型 (GanttSchedule)
    │
    ▼
Analyzer Chain       →  ProjectStats
    │
    ▼
Exporter             →  CSV / Markdown / HTML / Console
```

### 8.2 日期推导规则

```
1. 有明确开始日期 → 从该日期起算
2. 有前置任务依赖 → 从前置任务结束日期的下一个工作日开始
3. 两者都没有 → 使用项目默认开始日期（可配置）
4. 持续时间 = 工作日数（跳过周末、节假日、个人休假）
```

### 8.3 分析器链

```java
public class AnalysisEngine {
    private final WorkloadAnalyzer workloadAnalyzer;
    private final OverloadChecker overloadChecker;
    private final RiskDetector riskDetector;
    private final CriticalPathAnalyzer criticalPathAnalyzer;

    public ProjectStats analyze(GanttSchedule schedule) {
        var loads = workloadAnalyzer.analyze(schedule);
        var overloads = overloadChecker.check(loads);
        var risks = riskDetector.detect(schedule);
        return new ProjectStats(...);
    }
}
```

---

## 9. CLI 命令设计

```
ganttlens <command> [options]

Commands:
  analyze   解析 .puml 文件并输出统计信息
  check     检测排期风险和超载
  export    导出统计报表

Options:
  -f, --file <path>        输入 .puml 文件路径
  --person <name>          只统计指定人员
  --format <type>          输出格式: text(默认) / csv / markdown
  --out <path>             输出文件路径（export 命令）
  --start <date>           项目默认开始日期
  --verbose                显示详细信息
  --version                显示版本号
  -h, --help               显示帮助
```

### 示例输出

```bash
$ ganttlens analyze -f plan.puml

📊 项目概览
  项目名称: Q3 迭代排期
  时间跨度: 2026-07-01 ~ 2026-07-25 (18 个工作日)
  总任务数: 8
  总人天:   32

👤 人员投入
  张三: 10.5 天
  李四: 12.0 天
  王五:  9.5 天

⚠️  超载警告
  2026-07-08 张三 150% (需求分析 50% + 接口开发 100%)
  2026-07-15 李四 120% (联调测试 100% + 文档编写 20%)

📋 任务列表
  [1] 需求分析    张三(50%)   07-01~07-04  4天
  [2] 接口开发    李四(100%)  07-07~07-14  6天
  [3] 联调测试    张三(50%) 王五(100%) 07-15~07-17  3天
  ...
```

---

## 10. Maven 模块依赖

```
core (无外部依赖，只依赖 ANTLR4 runtime)
  ↑
cli (依赖 core + picocli)
  ↑
gui (依赖 core + JavaFX) [Phase 3]
```

父 POM 关键依赖管理：

```xml
<properties>
    <java.version>17</java.version>
    <antlr4.version>4.13.1</antlr4.version>
    <picocli.version>4.7.5</picocli.version>
    <junit.version>5.10.2</junit.version>
</properties>
```

---

## 11. 关键挑战

| 挑战 | 说明 |
|------|------|
| PlantUML Gantt 语法非标准 | 完整兼容成本高，需控制解析范围 |
| 自然语言式 DSL | 文本自由度高，统计结果可能不稳定 |
| 日期推导复杂 | 工作日、节假日、休假影响统计准确性 |
| 任务依赖链 | 可能存在循环依赖，需要拓扑排序 |

应对方式：

- 第一版只支持明确子集，对不支持语法给出 warning 而非静默忽略
- 提供推荐写法规范
- 后续逐步兼容更多 PlantUML 语法

---

## 12. MVP 交付物

| 交付物 | 说明 |
|--------|------|
| `ganttlens analyze` | 解析 .puml，输出人天统计和任务列表 |
| `ganttlens check` | 检测超载、未分配任务、无日期任务 |
| `ganttlens export` | 导出 CSV 格式的人力投入明细 |
| 单元测试 | 覆盖核心解析和分析逻辑 |
| 示例 .puml | 用于演示和测试的样本排期文件 |
