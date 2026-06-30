# GUI 绘图技术选型讨论

## Canvas vs Scene Graph 对比

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

### 3. 甘特图场景对比

#### 渲染细节

- **Canvas**：渐变色、纹理映射、自定义虚线样式更灵活
- **Scene Graph**：基本填充/描边足够，复杂效果需要 Canvas

#### 交互体验

**Canvas 实现点击**：
```java
canvas.setOnMouseMoved(e -> {
    // 需要遍历所有任务，手动计算鼠标位置
    for (Task task : tasks) {
        if (isInsideTask(e.getX(), e.getY(), task)) {
            // 高亮
            break;
        }
    }
    redraw();  // 重绘整个画布
});
```

**Scene Graph 实现点击**：
```java
Rectangle taskBar = new Rectangle(...);
taskBar.setOnMouseEntered(e -> taskBar.setFill(HIGHLIGHT_COLOR));
// JavaFX 自动处理碰撞检测
```

#### 拖拽功能

| 逻辑 | Canvas | Scene Graph |
|------|--------|-------------|
| 判断点击了哪个任务 | 自己写 | 自动 ✅ |
| 拖拽移动 | 自己写 | 自动 ✅ |
| **时间对齐**（吸附到日期） | 自己写 | 自己写 |
| **依赖约束**（不能早于前置） | 自己写 | 自己写 |
| **Undo/Redo** | 自己写 | 自己写 |

**结论**：拖拽的复杂度主要在**业务逻辑**，不在绘图技术

### 4. 自动化测试

| 测试类型 | Canvas | Scene Graph |
|----------|--------|-------------|
| **单元测试业务逻辑** | ✅ 一样 | ✅ 一样 |
| **UI 交互测试** | ❌ 困难 | ✅ 容易 |
| **视觉回归测试** | ❌ 很难 | ⚠️ 可以但麻烦 |
| **整体可测试性** | ⭐⭐ | ⭐⭐⭐⭐ |

#### Canvas 测试方案

**方案 1：Mock 测试（验证调用）**
```java
@Test
void testDrawTask() {
    GraphicsContext mockGc = mock(GraphicsContext.class);
    ganttRenderer.drawTask(mockGc, task);
    
    // 只能验证调用了什么方法，不能验证画了什么
    verify(mockGc).fillRect(eq(10.0), eq(20.0), eq(100.0), eq(30.0));
}
```

**方案 2：截图对比（视觉回归）**
```java
@Test
void testGanttRender() {
    WritableImage image = canvas.snapshot(null, null);
    BufferedImage expected = ImageIO.read(new File("baseline.png"));
    assertTrue(compareImages(expected, image));
}
```
- 问题：字体渲染差异、抗锯齿差异、CI 环境不一致

#### Scene Graph 测试方案

```java
@Test
void testTaskBarDisplayed() {
    Rectangle taskBar = lookup(".task-bar").query();
    assertNotNull(taskBar);
    assertEquals(100, taskBar.getWidth());
    assertEquals(Color.BLUE, taskBar.getFill());
}
```

### 5. 推荐场景

| 需求场景 | 推荐方案 |
|----------|----------|
| 需要精细渲染控制（渐变、纹理、自定义形状） | Canvas |
| 需要丰富交互（点击、拖拽、悬停） | Scene Graph |
| 大量元素（>1000） | Canvas |
| **快速开发 MVP** | **Scene Graph** |
| **重视自动化测试** | **Scene Graph** |
| 未来可能加复杂渲染 | Canvas |

## 当前决策

**待定**：需要进一步讨论确定最终方案

## 待讨论事项

1. GanttLens 甘特图的预期任务数量规模？
2. 是否需要视觉回归测试？
3. 未来是否需要复杂渲染效果（渐变、纹理）？
4. 开发时间限制？
