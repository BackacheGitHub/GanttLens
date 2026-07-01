# GUI 绘图技术选型决策

## 最终决策：混合架构（Scene Graph + Canvas）

### 方案概述

| 层 | 技术 | 理由 |
|---|---|---|
| 应用框架（菜单、侧栏、对话框、属性面板） | **Scene Graph** | 布局、交互、可测试性全面占优 |
| 甘特图绘图区 | **Canvas** | 200+ 任务的渲染性能更优，像素控制更精确 |
| 布局计算逻辑 | **纯 Java 类**（`GanttLayoutEngine`） | 可独立单元测试，Humble Object 模式 |
| 自动化测试 | **逻辑测试 + 基准截图对比** | 逻辑测试为主力，截图对比为辅助信号 |

### 决策依据

#### 1. 规模需求
- 目标支持 200+ 任务、多项目并行管理、跨项目人员协同
- 200 个任务条 + 依赖箭头 + 时间轴在 Scene Graph 下会产生 2000-4000 个节点，接近性能边界
- Canvas 在此规模下渲染性能更稳定

#### 2. 交互需求
- 交互以按钮、下拉框、对话框为主，不强求拖拽
- 拖拽的复杂度主要在业务逻辑（时间对齐、依赖约束、Undo/Redo），不在绘图技术
- 甘特图本身更多是"展示 + 选择"，是 Canvas 的甜区

#### 3. 视觉风格
- 参考 OmniPlan：功能性视觉元素（进度条、状态色块）优先，拒绝纯装饰（渐变、纹理）
- 扁平色块 + 进度填充 + 清晰依赖箭头的风格，Canvas 和 Scene Graph 均可实现

#### 4. 数据架构
- 采用临时编辑 + 显式保存机制：修改先作用于内存任务模型，支持 Undo/Redo
- 仅在用户主动保存时序列化回 PlantUML 源码
- 渲染技术与数据架构完全解耦

#### 5. 可测试性
- 核心可测试性来自**布局逻辑层**（`GanttLayoutEngine`），不来自渲染层
- 采用 Humble Object 模式：`layoutTasks(tasks, config) -> List<TaskLayout>` 为纯函数，可独立单元测试
- 截图对比作为辅助信号，捕捉"逻辑测试通过但视觉表现变了"的边缘情况
- 截图对比方案：同机基准对比 + 差异人工审核，阈值 > 1% 时标记失败

### 业界参考
- VS Code：Canvas 渲染编辑器，测试 TextModel 而非像素
- JetBrains IDE：自绘编辑器，测试 PSI 模型和编辑器行为
- Flutter/React 社区：测组件状态和行为，截图对比（Percy/Chromatic）为补充手段
- Martin Fowler "Humble Object" 模式：将复杂对象拆分为可测逻辑 + 不可测展示层

### 甘特图 Canvas 区域的技术方案

```java
// 架构分层
gui/
  GanttCanvasView.java       // Canvas 渲染（哑终端，只做绘制命令）
  GanttLayoutEngine.java     // 纯 Java 布局计算（可独立测试）
  GanttInteractionHandler.java // 鼠标事件 -> 命中检测 -> 业务操作
  TaskSelectionModel.java    // 选中状态管理
```

- `GanttLayoutEngine`：输入任务列表 + 视图配置，输出每个任务的坐标、尺寸
- `GanttCanvasView`：遍历布局结果，调用 GraphicsContext 绘制
- `GanttInteractionHandler`：鼠标坐标 -> 命中检测（委托给 LayoutEngine 的纯函数）-> 触发操作

---

## 附录：Canvas vs Scene Graph 详细对比

### 1. 基本概念

| 特性 | Canvas | Scene Graph |
|------|--------|-------------|
| 模式 | 立即模式 (Immediate) | 保留模式 (Retained) |
| 原理 | 绘制命令 → 像素缓冲区 | 创建对象 → 对象持续存在 |
| 所属包 | `javafx.scene.canvas` | `javafx.scene` |

### 2. 细节控制能力

| 能力 | Canvas | Scene Graph |
|------|--------|-------------|
| 精确像素控制 | ✅ 强 | ❌ 弱 |
| 复杂形状（曲线、路径） | ✅ 灵活 | ⚠️ 需要 Path |
| 交互（点击、悬停） | ❌ 需自己计算 | ✅ 天然支持 |
| 动画 | ❌ 需手动刷新 | ✅ 内置 Timeline |
| 布局管理 | ❌ 手动定位 | ✅ VBox/HBox/BorderPane |
| 性能（1000+ 元素） | ✅ 好 | ⚠️ 可能卡顿 |

### 3. 自动化测试对比

| 测试类型 | Canvas | Scene Graph |
|----------|--------|-------------|
| **布局/业务逻辑测试** | ✅ 一样（纯函数） | ✅ 一样（纯函数） |
| **UI 交互测试** | ❌ 困难 | ✅ 容易 |
| **视觉回归测试** | ⚠️ 截图对比（辅助） | ⚠️ 截图对比（辅助） |
| **整体可测试性** | ⭐⭐⭐⭐（配合分层架构） | ⭐⭐⭐⭐ |
