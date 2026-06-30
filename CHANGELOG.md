# Changelog

本文件记录 GanttLens 项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-06-30

### 新增

#### 第一阶段：PlantUML 语法扩展
- `as` 别名支持：`[设计] as [D]`
- `lasts` 关键字：替代 `requires` 表示任务持续时间
- `then` 依赖链：简化连续任务的依赖声明
- `->` 箭头依赖：`[T1] -> [T2]` 创建依赖关系
- `Project starts DATE`：设置项目起始日期
- `starts at [task]'s end/start`：任务依赖
- `happens` 里程碑：`发布 happens at [测试]'s end`
- 注释支持：`'` 行注释和 `/'...'/` 块注释
- `weeks` 持续时间单位：`[任务] lasts 2 weeks`
- `is completed`：标记任务完成状态
- `DATE is closed/open`：日期开关
- `DATE to DATE is closed`：日期范围关闭
- 人员休假：`{John} is off on DATE`
- 错误恢复：未知语法不会导致崩溃
- 循环依赖检测：通过迭代限制避免无限循环

#### 第二阶段：分析维度扩展
- **关键路径分析**：找出影响项目工期的关键任务链
  - 拓扑排序算法
  - 最早/最晚开始结束时间计算
  - 浮动时间计算
- **资源平衡建议**：为过载情况提供调优建议
  - 关键路径任务建议减少分配比例
  - 非关键路径任务建议推迟

#### 第三阶段：GUI 模块
- JavaFX 图形界面
- 代码编辑器
- 文件打开功能
- 分析结果展示

### 测试
- 68 个单元测试，全部通过
- 覆盖所有核心功能和边界情况

## [0.1.0] - 2026-06-15

### 新增
- 项目初始化
- 基础 PlantUML 语法解析
- 工作量分析
- 过载检测
- Excel 导出
