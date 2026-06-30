# GanttLens

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
# 启动 GUI
cd gui
mvn javafx:run
```

## 项目结构

```
GanttLens/
├── core/           # 核心模块：解析、分析、导出
├── cli/            # 命令行界面
├── gui/            # JavaFX 图形界面
└── docs/           # 文档
```

## 文档

- [架构设计](docs/architecture.md)
- [开发计划](docs/development-plan.md)
- [语法参考](docs/syntax-reference.md)

## 版本历史

- v1.0.0 - 初始版本，支持核心语法和分析功能
- v1.1.0 - 完成语法扩展和关键路径分析
# GanttLens

PlantUML 甘特图分析与洞察工具

## 文档

- [架构设计](docs/architecture.md)

## 环境要求

- Java 17+

## 构建

```bash
./mvnw clean package
```

## 使用

```bash
java -jar cli/target/ganttlens-cli.jar analyze -f plan.puml
```
