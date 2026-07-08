# GanttLens

> **注意**：这是一个学习性质的玩具项目，主要用于探索 ANTLR 语法解析、关键路径算法和 JavaFX 渲染等技术，不具备生产环境的健壮性和完整性。

PlantUML 甘特图分析与洞察工具 - 解析 PlantUML 甘特图语法，提供工作量分析、关键路径分析和资源平衡建议。

## 功能特性

### 核心功能

- **PlantUML 语法解析** - 支持完整的 PlantUML 甘特图语法
- **工作量分析** - 计算每人每日工作负荷
- **过载检测** - 识别资源过载情况（>100%）
- **关键路径分析** - 找出影响项目工期的关键任务链
- **资源平衡建议** - 为过载情况提供调优建议

### 支持的语法

- `as` 别名：`[设计] as [D]`
- `lasts` 关键字：`[任务] lasts 5 days`
- `then` 依赖链：`then [任务]`
- `->` 箭头依赖：`[T1] -> [T2]`
- `Project starts DATE`：项目起始日期
- `starts at [task]'s end/start`：任务依赖
- `happens` 里程碑：`发布 happens at [测试]'s end`
- 注释支持：`'` 行注释和 `/'...'/` 块注释

## 环境要求

- Java 17+
- Maven 3.8+

## 构建

```bash
# 完整构建
./mvnw clean package

# 仅构建 core 模块
./mvnw clean package -pl core

# 跳过测试构建
./mvnw clean package -DskipTests
```

## 使用

### CLI 命令

```bash
# 分析甘特图
java -jar cli/target/ganttlens-cli.jar analyze -f plan.puml

# 导出 Excel
java -jar cli/target/ganttlens-cli.jar export -f plan.puml -o output.xlsx
```

### GUI 应用

```bash
# 如果修改了 core 模块，先安装 core 到本地仓库
./mvnw install -pl core -DskipTests

# 如果修改了 gui 模块，只需重新编译 gui
./mvnw compile -pl gui

# 启动 GUI（pom.xml 已配置 mainClass）
./mvnw exec:java -pl gui
```

启动后可通过文件选择器打开 `.puml` 文件，在左侧编辑器中编辑源码，点击"解析"按钮后右侧甘特图实时更新。
支持任务选中、属性编辑、Undo/Redo 和 Excel 导出。详见 [GUI 产品需求文档](docs/gui-prd.md)。

## 项目结构

```
GanttLens/
├── core/           # 核心模块：解析、分析、导出
├── cli/            # 命令行界面（analyze + export）
├── gui/            # JavaFX 图形界面（甘特图 + 属性编辑）
└── docs/           # 文档
    └── archive/    # 历史文档（已完成的发展计划等）
```

## 文档

- [架构设计](docs/architecture.md) — 模块结构、数据流、技术栈、设计决策
- [GUI 产品需求文档](docs/gui-prd.md) — 用户故事、UI 布局、交互流程、技术选型
- [语法参考](docs/syntax-reference.md) — PlantUML Gantt 支持的语法清单
- [开发计划（归档）](docs/archive/development-plan.md) — 历史迭代记录（已全部完成）

# 版本历史

- v0.1.0 - 项目初始化，基础语法解析、工作量分析、过载检测、Excel 导出
- v1.0.0 - 完整语法覆盖（别名、依赖链、里程碑、注释等），关键路径分析、资源平衡建议
- v1.1.0 - JavaFX GUI 模块（甘特图渲染、任务选中、属性编辑、Undo/Redo）
- Unreleased - 部分进度语法（`is X% complete`）、GUI 分组头渲染优化、JaCoCo 覆盖率

## 心路历程

这个项目的起点很简单：我自己有项目管理的需求，用过 OmniPlan（但只能在 Mac 上用），试过 Microsoft Project（太重了），最终发现 PlantUML 甘特图语法小而美——纯文本、版本控制友好、写起来很快。但 PlantUML 只能画图，做不了排期分析，于是决定自己做一个分析工具。

技术上选择了 ANTLR 做语法解析、JavaFX 做 GUI，整个开发过程大量借助了 AI 辅助编码。从 0 到 1 搭建了完整的解析器、分析引擎和 GUI 渲染，跑通了"PlantUML 文本 → 甘特图可视化 → 关键路径/过载分析"的全链路。

在考虑下一步方向时，认真思考了几个可能的演进路径：

- **GUI 交互增强**（拖拽调期、右键 AI 建议、自动重平衡）——但发现 PlantUML 语法本身无法无损表达这些操作（如"依赖前置任务但推迟 3 天"），要么受限于格式，要么放弃 PlantUML 改用结构化存储，后者又让产品定位模糊
- **AI 集成**（MCP 接口、对话式排期）——方向有趣，但 PlantUML 甘特图的用户群体太小，天花板明显
- **做成完整的项目管理工具**——本质上是在做一个带 AI 的 MS Project，工程量和竞争壁垒都不是一个人能解决的

最终决定在这里停下来。不是因为它失败了，而是想清楚了继续投入的边际收益已经不高。代码留在这里，作为一段完整的工程实践记录。

如果这个项目对你有启发——无论是技术实现、架构设计，还是产品方向的思考——欢迎 fork 继续。
