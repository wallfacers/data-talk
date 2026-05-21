# 用户时区配置与时间格式化

## 动机

数据库中的时间类型字段（DATETIME / TIMESTAMP / DATE 等）存储通常为 UTC，但不同地区用户对显示格式有不同偏好。中国用户习惯 `yyyy-MM-dd HH:mm:ss`，其他地区可能偏好 `MM/dd/yyyy HH:mm:ss` 等。当前系统无时区感知，所有时间值按数据库驱动返回的原始字符串直接展示。

## 需求

- 在"设置 > 通用"页面新增时区选择器，用户可手动选择目标时区（如 `Asia/Shanghai`、`America/New_York`）
- 时区偏好持久化到后端（复用现有 user preferences 存储）
- SQL 查询结果中，时间类型字段按用户配置的时区进行格式转换后展示
- 默认时区为系统时区
- 格式规则按地区惯例自动匹配，中国地区默认 `yyyy-MM-dd HH:mm:ss`
- 用户可进一步自定义时间格式模板

## 影响范围

- 后端：`user_preferences` 表新增 timezone / dateFormat 字段；`JdbcResultValueNormalizer` 或结果后处理层增加时区转换
- 前端：Settings 通用页新增时区选择器组件；SQL 结果表格对时间列应用格式化
- 数据源兼容：需按 [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) 评估各数据库时间类型行为差异（MySQL DATETIME 无时区 / PostgreSQL TIMESTAMP WITH TIME ZONE 等）
