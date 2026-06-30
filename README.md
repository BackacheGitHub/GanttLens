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
