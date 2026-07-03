# GanttLens 结构化推进计划

## 项目现状

| 模块 | 状态 | 关键文件 |
|------|------|---------|
| core (解析/分析/导出) | MVP 可用 | 11 个 Java + 1 个 g4 |
| cli | MVP 可用 | 3 个 Java |
| gui | 仅骨架 | 1 个 package-info.java |

---

## 第一阶段：扩展 PlantUML 语法支持 ✅ 已完成

### 迭代 1.1 — 核心语法补全（覆盖 80% 常见写法）✅

| # | 任务 | 关键文件 | 验证 |
|---|------|---------|------|
| 1 | `as` 别名 `[设计] as [D]` | `PlantUMLGantt.g4`, `GanttParseListener.java` | 新增测试用例 |
| 2 | `lasts N days` 替代 `requires` | `PlantUMLGantt.g4` | 测试 `[任务] lasts 5 days` |
| 3 | `then` 简化依赖链 | `PlantUMLGantt.g4`, `GanttParseListener.java` | 连续 3 个 then 任务 |
| 4 | `->` 箭头依赖 `[T1] -> [T2]` | `PlantUMLGantt.g4` | 链式箭头依赖 |
| 5 | `Project starts DATE` | `PlantUMLGantt.g4` | 项目起始日生效 |
| 6 | `starts DATE` / `ends DATE` 绝对日期 | `PlantUMLGantt.g4` | 日期范围任务 |
| 7 | `happens` 里程碑 | `PlantUMLGantt.g4`, 新建 `Milestone.java` | 里程碑正确标记 |
| 8 | 注释支持 `'` 和 `/'...'/` | `PlantUMLGantt.g4` 词法 | 含注释文件正常解析 |

**新增测试文件：** `then-tasks.puml`, `arrow-deps.puml`, `milestones.puml`, `full-syntax.puml`

### 迭代 1.2 — 增强指令 ✅

| # | 任务 | 说明 | 自动化验收标准 |
|---|------|------|----------------|
| 1 | `is colored in Green` | Task 增加 color 字段 | 单元测试：解析 `[T1] is colored in Green`，断言 `task.color().equals("Green")` |
| 2 | `is 40% completed` | TaskStatus 扩展完成度 | 单元测试：解析 `[T1] is 40% completed`，断言 `task.completionPercent() == 40` |
| 3 | `and` 组合语法 | `starts DATE and ends DATE` | 单元测试：解析 `starts 2026-07-01 and ends 2026-07-10`，断言 startDate 和 endDate 正确 |
| 4 | `DATE to DATE is closed` | 日期范围关闭 | 单元测试：解析 `2026-07-01 to 2026-07-05 is closed`，断言 holidays 包含 5 天 |
| 5 | `is open` | 在关闭范围内打开特定日期 | 单元测试：解析范围关闭 + `2026-07-03 is open`，断言 07-03 不在 holidays |
| 6 | `requires N weeks` | duration 支持周 | 单元测试：周末关闭时 `requires 2 weeks`，断言 `durationDays == 10` |

### 迭代 1.3 — 解析健壮性 ✅

| # | 任务 | 说明 | 自动化验收标准 |
|---|------|------|----------------|
| 1 | 错误恢复 | 不支持语法给 warning 而非崩溃 | 单元测试：解析含未知指令的 puml，断言不抛异常且返回有效 Schedule |
| 2 | `D+N` 相对日期 | `starts D+15` | 单元测试：项目起始日 07-01，`starts D+15`，断言 startDate == 07-16 |
| 3 | 循环依赖检测 | `resolveTaskDates()` 拓扑排序 | 单元测试：解析 A→B→C→A 的 puml，断言抛出或记录 CircularDependencyException |

---

## 第二阶段：增加分析维度 ✅ 已完成

### 迭代 2.1 — 关键路径分析 ✅

**解决什么问题**：项目排期定了，但哪个任务延期会直接导致整个项目延期？

**关键路径** = 项目中耗时最长的任务链，这条链上没有任何缓冲时间。

```
示例：

[设计] requires 3 days          ─┐
                                 ├─→ [开发] requires 5 days ──→ [测试] requires 2 days
[需求文档] requires 2 days      ─┘

设计 → 开发 → 测试 = 3 + 5 + 2 = 10 天（关键路径）
需求文档 → 开发 → 测试 = 2 + 5 + 2 = 9 天（有 1 天浮动）
```

**关键路径分析告诉你**：
- 「设计」延期 1 天 → 项目整体延期 1 天
- 「需求文档」延期 1 天 → 项目不受影响（因为有 1 天浮动）

**对管理者的价值**：知道该重点盯哪些任务。

| # | 任务 | 关键文件 | 自动化验收标准 |
|---|------|----------|----------------|
| 1 | 新建 `CriticalPathAnalyzer.java` | `core/.../analyzer/` — 拓扑排序 + 最长路径 | 单元测试：给定已知关键路径的 puml，断言返回正确的 criticalTaskIds |
| 2 | 新建 `CriticalPathResult.java` | `core/.../model/` — record: criticalTaskIds, totalDuration, taskFloats | 单元测试：断言 totalDuration == 关键路径总天数，非关键任务 float > 0 |
| 3 | 集成到 `AnalysisEngine` | `analyze()` 中调用 | 单元测试：调用 `analyze()`，断言返回的 ProjectStats 包含非空 criticalPath |
| 4 | 集成到 `ProjectStats` | 增加 criticalPath 字段 | 单元测试：断言 ProjectStats.criticalPath() 不为 null |
| 5 | CLI 输出关键路径 | `AnalyzeCommand.java` 增加段落 | 集成测试：运行 CLI，断言输出包含 "Critical Path" 和任务名称 |

### 迭代 2.2 — 资源平衡建议 ✅

**解决什么问题**：张三某天同时被两个任务分配，负荷 200%，怎么调？

```
张三的工作量热力图：
  07-01  07-02  07-03  07-04
   100%   200%   200%   100%
              ↑      ↑
          [开发A] [开发B] 同时进行
```

**资源平衡建议**：
- 开发 A 是关键路径任务 → 不能推迟
- 开发 B 有 2 天浮动 → 建议推迟 1 天

**输出**：
```
建议：张三 07-03 的「开发B」可推迟至 07-04 开始
原因：开发B 有 2 天浮动，且非关键路径
```

**对管理者的价值**：不用手动算谁该调，工具直接给建议。

**两者关系**：迭代 2.1 必须先做（提供浮动时间数据），2.2 才能用。

| # | 任务 | 关键文件 | 自动化验收标准 |
|---|------|----------|----------------|
| 1 | 新建 `ResourceBalancer.java` | 输入过载记录，输出建议 | 单元测试：给定过载场景，断言返回非空建议列表 |
| 2 | 新建 `BalanceSuggestion.java` | record: person, suggestTask, suggestStart, reason | 单元测试：断言 suggestion 包含正确 person、task、reason |
| 3 | 集成到 `AnalysisEngine` | 过载时自动运行 | 单元测试：调用 `analyze()`，断言过载时返回 balanceSuggestions |
| 4 | CLI/Excel 输出建议 | 报告和导出中展示 | 集成测试：运行 CLI，断言输出包含 "Suggestion" 和任务名称 |

---

## 第三阶段：GUI 模块 ✅ 已完成

### 迭代 3.1 — 基础框架 ✅

| # | 任务 | 关键文件 | 自动化验收标准 |
|---|------|----------|----------------|
| 1 | 启用 JavaFX 依赖 | `gui/pom.xml`, `javafx-maven-plugin` | 构建测试：`mvn compile -pl gui` 成功 |
| 2 | `GuiApp.java` 入口 | JavaFX Application | 单元测试：断言 GuiApp 类存在且继承 Application |
| 3 | 主界面 FXML | 三栏布局：文件树/甘特图/分析面板 | 单元测试：断言 FXML 文件可解析为有效 Node |
| 4 | `MainController.java` | 文件选择 + 调用 core | 集成测试：断言 controller 可加载 puml 并返回有效 schedule |

### 迭代 3.2 — 可视化面板 ✅

| # | 任务 | 说明 | 自动化验收标准 |
|---|------|------|----------------|
| 1 | 甘特图视图 | Canvas/Pane 绘制条形图 | 单元测试：断言 GanttChartView 可渲染任务列表，返回非空 Node |
| 2 | 工作量热力图 | 颜色编码表格，过载红色高亮 | 单元测试：断言 WorkloadHeatmapView 可显示负荷数据，过载单元格样式正确 |
| 3 | 统计摘要面板 | 关键指标 | 单元测试：断言 StatsPanel 显示的数值与 ProjectStats 一致 |

---

## 第四阶段：文档与打包 ✅ 已完成

| # | 任务 | 说明 |
|---|------|------|
| 1 | README.md | 安装说明、使用示例、语法清单 |
| 2 | CHANGELOG.md | 版本变更记录 |
| 3 | 语法参考文档 | `docs/syntax-reference.md` |
| 4 | maven-shade-plugin | fat jar 打包 |
| 5 | jpackage | 原生安装包 (.msi) |

---

## 版本里程碑

```
v1.1  ← 迭代 1.1 完成（核心语法补全）
v2.0  ← 迭代 2.1 完成（关键路径分析）
v3.0  ← 迭代 3.2 完成（GUI 可视化）
```

## 验收标准（每个迭代通用）

1. `mvn clean test` 全量通过
2. `ganttlens analyze test.puml` CLI 输出正确
3. `ganttlens export test.puml` 导出文件可正常打开
4. 新增测试覆盖率达到变更代码 80% 以上
