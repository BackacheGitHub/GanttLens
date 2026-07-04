# GanttLens 架构文档

## 1. 项目概述

**GanttLens** 是 PlantUML 甘特图的分析增强工具，解析 `.puml` 文件中的排期信息，提供人力统计、负载分析、关键路径检测和资源平衡建议。

核心理念：**用 PlantUML 写排期，用 GanttLens 看人力。**

> GanttLens is an independent analysis tool that supports a subset of PlantUML Gantt syntax.
> GanttLens is not affiliated with or endorsed by PlantUML.

### 目标用户

- 技术负责人 / 研发组长
- 项目经理 / 交付经理
- 喜欢文档即代码（Docs-as-Code）工作流的工程团队

### 差异化价值

| 对比对象 | GanttLens 的优势 |
|----------|-----------------|
| 单纯 PlantUML | 不只画图，还回答人力和风险问题；支持统计图表和 Excel 导出 |
| Jira / MS Project | 文本源文件可 Git 版本化；更轻量，适合工程文档体系 |
| Mermaid Gantt | PlantUML 的 `{人员:百分比}` 语法更适合人天计算和资源分析 |

---

## 2. 技术栈

| 层次 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 17+ | LTS，使用 records、text blocks、switch expressions |
| 语法解析 | ANTLR4 | 4.13.1 | PlantUML Gantt DSL 解析核心 |
| CLI 框架 | picocli | 4.7.5 | 注解式子命令 |
| GUI 框架 | JavaFX | 21+ | 混合架构：Scene Graph + Canvas |
| Excel 导出 | Apache POI | 5.2.5 | xlsx 多 sheet 工作簿 |
| 日志 | Log4j2 | 2.21.1 | 日志实现 |
| 构建工具 | Maven | 3.9+ | 多模块管理，maven-shade-plugin fat jar |
| 单元测试 | JUnit 5 + AssertJ | 5.10.2 / 3.25.3 | 14 个测试类，覆盖解析、分析、导出、GUI |
| 覆盖率 | JaCoCo | 0.8.12 | 行/分支/指令级覆盖率报告，自动排除 ANTLR 生成代码 |

---

## 3. 模块结构

```
GanttLens/
├── pom.xml                    # 父 POM（版本管理 + 插件配置）
│
├── core/                      # 核心模块：解析 + 分析 + 导出
│   ├── pom.xml
│   └── src/main/
│       ├── antlr4/
│       │   └── PlantUMLGantt.g4          # ANTLR4 语法定义
│       └── java/com/ganttlens/
│           ├── parser/                     # 解析层
│           │   ├── GanttFileParser.java    # 入口：.puml → GanttSchedule
│           │   └── GanttParseListener.java # ANTLR Visitor → 领域模型
│           ├── analyzer/                   # 分析层
│           │   ├── AnalysisEngine.java     # 编排器：调度所有分析器
│           │   ├── WorkloadAnalyzer.java   # 人天 + 每日负载计算
│           │   ├── OverloadChecker.java    # 超载检测（>100%）
│           │   ├── CriticalPathAnalyzer.java # 关键路径（拓扑排序 + 最长路径）
│           │   ├── ResourceBalancer.java   # 资源平衡建议
│           │   └── GanttLayoutEngine.java  # 甘特图布局计算（纯函数）
│           ├── export/                     # 导出层
│           │   └── ExcelGanttExporter.java # Excel 多 sheet 导出
│           └── model/                      # 数据模型（全部 record）
│               ├── Task.java               # 任务（含 color、progressPercent）
│               ├── Assignment.java         # 人员分配（person + ratio）
│               ├── TaskStatus.java         # 枚举：PENDING/IN_PROGRESS/COMPLETED/BLOCKED
│               ├── GanttSchedule.java      # 解析结果（config + tasks）
│               ├── ScheduleConfig.java     # 日历配置（标题、起始日、节假日、休假）
│               ├── ProjectStats.java       # 分析结果聚合
│               ├── PersonDailyLoad.java    # 每人每日负载
│               ├── OverloadRecord.java     # 超载记录
│               ├── CriticalPathResult.java # 关键路径结果（任务链 + 浮动时间）
│               ├── BalanceSuggestion.java  # 资源平衡建议
│               ├── TaskLayout.java         # 布局坐标/尺寸（GUI 用）
│               ├── LayoutConfig.java       # 视图配置（缩放、行高等）
│               ├── TaskSelectionModel.java # 选中状态管理（GUI 用）
│               ├── Command.java            # Undo/Redo 命令接口
│               ├── CommandStack.java       # 操作栈（Undo/Redo）
│               └── EditTaskCommand.java    # 编辑任务命令实现
│
├── cli/                       # CLI 模块
│   ├── pom.xml
│   └── src/main/java/com/ganttlens/cli/
│       ├── CliApp.java          # picocli 主入口
│       ├── AnalyzeCommand.java  # analyze 子命令
│       └── ExportCommand.java   # export 子命令（Excel 导出）
│
├── gui/                       # GUI 模块（JavaFX）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ganttlens/gui/
│       │   ├── GuiApp.java                  # JavaFX Application 入口
│       │   ├── MainController.java          # 主控制器（文件加载 + 解析触发）
│       │   ├── GanttCanvasView.java         # Canvas 渲染层（哑终端）
│       │   ├── GanttInteractionHandler.java # 鼠标事件 → 命中检测 → 业务操作
│       │   └── GanttColorMapper.java        # 任务状态 → 颜色映射
│       └── resources/
│           ├── fxml/main.fxml               # 主界面 FXML 布局
│           └── css/style.css                # 样式表
│
└── docs/                      # 文档
    ├── architecture.md        # 本文档
    ├── gui-prd.md             # GUI 产品需求文档（含技术选型）
    ├── syntax-reference.md    # PlantUML Gantt 语法参考
    └── archive/               # 历史文档
        └── development-plan.md # 开发计划（已全部完成）
```

### 模块依赖

```
core  ← 无外部业务依赖（仅 ANTLR4 runtime + POI + Log4j2）
  ↑
cli   ← core + picocli
  ↑
gui   ← core + JavaFX（javafx-controls / javafx-fxml）
```

---

## 4. 核心数据流

```
.puml 文件
    ↓  [GanttFileParser + ANTLR4]
GanttSchedule（ScheduleConfig + List<Task>）
    ↓  [AnalysisEngine]
    │    ├─ WorkloadAnalyzer   → personManDays + dailyLoads
    │    ├─ OverloadChecker    → overloads
    │    ├─ CriticalPathAnalyzer → criticalPath
    │    └─ ResourceBalancer   → balanceSuggestions
    ↓
ProjectStats（聚合结果）
    ↓
  ├─ [CLI] AnalyzeCommand  → 控制台文本输出
  ├─ [CLI] ExportCommand   → Excel 文件（ExcelGanttExporter）
  └─ [GUI] GanttLayoutEngine → List<TaskLayout> → GanttCanvasView → Canvas 渲染
```

### 日期推导规则

1. 有明确开始日期（`starts DATE`）→ 从该日期起算
2. 有前置任务依赖（`starts at [X]'s end`、`then`、`->`）→ 从前置任务结束的下一个工作日开始
3. 均无 → 使用 `project starts DATE` 配置的项目默认开始日期
4. 持续时间按工作日计算（跳过周末关闭日、节假日、个人休假）

---

## 5. 核心数据模型

```java
// 任务（record，不可变）
public record Task(
    String id,                    // 自动生成
    String name,                  // 任务名称
    String group,                 // 所属阶段（可选，来自 -- 分组 --）
    LocalDate startDate,          // 计算后的开始日期
    LocalDate endDate,            // 计算后的结束日期
    int durationDays,             // 持续工作日数
    List<Assignment> assignments, // 人员分配列表
    List<String> dependencyIds,   // 前置依赖任务 ID
    TaskStatus status,            // PENDING / IN_PROGRESS / COMPLETED / BLOCKED
    String color,                 // 颜色标记（来自 is colored in X）
    int progressPercent           // 完成百分比（0-100）
) {}

// 人员分配
public record Assignment(String person, double ratio) {}  // ratio: 0.0-1.0

// 解析结果
public record GanttSchedule(ScheduleConfig config, List<Task> tasks) {}

// 日历配置
public record ScheduleConfig(
    String title,
    LocalDate projectStartDate,
    boolean saturdayClosed,
    boolean sundayClosed,
    Set<LocalDate> holidays,
    Set<PersonOffEntry> personOffDays
) {}

// 分析结果聚合
public record ProjectStats(
    double totalManDays,
    Map<String, Double> personManDays,
    List<PersonDailyLoad> dailyLoads,
    List<OverloadRecord> overloads,
    List<Task> tasks,
    CriticalPathResult criticalPath,
    List<BalanceSuggestion> balanceSuggestions
) {}
```

---

## 6. 分析引擎

`AnalysisEngine` 是编排器，按顺序调用四个分析器：

```java
public class AnalysisEngine {
    private final WorkloadAnalyzer workloadAnalyzer;
    private final OverloadChecker overloadChecker;
    private final CriticalPathAnalyzer criticalPathAnalyzer;
    private final ResourceBalancer resourceBalancer;

    public ProjectStats analyze(GanttSchedule schedule) { ... }
}
```

| 分析器 | 职责 | 算法 |
|--------|------|------|
| WorkloadAnalyzer | 计算每人总人天和每日负载 | 遍历任务 × 工作日 × 分配比例 |
| OverloadChecker | 检测某人某日负载 > 100% | 聚合每日负载，超阈值记录 |
| CriticalPathAnalyzer | 找出项目最长任务链 | 拓扑排序 + 最早/最晚时间 + 浮动计算 |
| ResourceBalancer | 为过载提供调优建议 | 结合关键路径浮动，建议推迟或减配 |

---

## 7. GUI 架构

GUI 采用**混合架构**（Scene Graph + Canvas），详见 [gui-prd.md](gui-prd.md)。

### 分层设计（Humble Object 模式）

| 组件 | 职责 | 可测试性 |
|------|------|---------|
| `GanttLayoutEngine`（core） | 任务列表 + LayoutConfig → 每个任务的屏幕坐标/尺寸 | ✅ 纯函数，可直接 JUnit 测试 |
| `GanttCanvasView` | 遍历 TaskLayout，调用 GraphicsContext 绘制 | ✅ 集成测试验证渲染流程 + 人工视觉验证 |
| `GanttInteractionHandler` | 鼠标事件 → 命中检测 → 触发业务操作 | ✅ 可注入 mock 依赖测试 |
| `TaskSelectionModel`（core） | 管理选中状态，与属性面板绑定 | ✅ 纯状态机，可直接测试 |
| `CommandStack`（core） | Undo/Redo 操作栈（Command 模式） | ✅ 纯逻辑，可直接测试 |

### 测试覆盖说明

`GanttCanvasView` 的绘制逻辑通过集成测试验证端到端流程：
- 默认示例（含分组头）能正常加载、解析、渲染且无异常

由于 JavaFX Canvas 的特性，精确的绘制参数验证（如 y 坐标、颜色值）需要 Mock 框架支持。当前阶段依赖人工视觉验证确保视觉效果正确。未来实施 PRD 中规划的视觉回归测试时，将引入 TestFX + Mockito 形成完整的 UI 测试体系。

### 编辑模型

- 所有界面修改先作用于内存中的 `GanttSchedule` 副本
- 支持 Undo/Redo（`CommandStack` + `EditTaskCommand`）
- 仅在用户显式保存时才序列化回 PlantUML 源码（整体重新序列化）

---

## 8. CLI 命令

```
ganttlens <command> [options]

Commands:
  analyze   解析 .puml 文件，输出人天统计、关键路径、过载警告和资源平衡建议
  export    导出 Excel 报表（甘特图 + 热力图 + 统计 + 任务明细）

Options:
  -f, --file <path>    输入 .puml 文件路径（必选）
  -o, --output <path>  输出文件路径（export 命令）
  --person <name>      只统计指定人员
  --verbose            显示详细信息
  -h, --help           显示帮助
```

---

## 9. 关键设计决策

| 决策 | 原因 |
|------|------|
| 只支持 PlantUML Gantt 语法子集 | 完整兼容成本高；不支持的语法给 warning 而非静默忽略 |
| Task 为不可变 record | 解析结果是快照；Undo/Redo 通过 Command 模式管理副本 |
| 布局逻辑在 core 而非 gui | GanttLayoutEngine 可在无 JavaFX 环境下单元测试 |
| 保存时整体重新序列化 | 避免增量 patch 的复杂性；保证源码与内存模型一致 |
| Excel 为唯一导出格式 | POI 可实现多 sheet 富格式报表；CSV/MD 暂未实现 |

---

## 10. 已知局限

| 局限 | 说明 |
|------|------|
| PlantUML 语法非标准 | 部分高级语法（如自定义日历、嵌套分组）暂不支持 |
| 无风险检测模块 | 早期设计中规划的 RiskDetector 未实现；超载检测部分覆盖此功能 |
| 无 CSV/Markdown/HTML 导出 | 目前仅支持 Excel 和控制台文本输出 |
| GUI 无拖拽交互 | 任务日期调整通过属性面板输入，不支持拖拽任务条 |
| 不支持多项目并行分析 | 每次只能分析一个 .puml 文件 |
