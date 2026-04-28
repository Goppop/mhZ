# 政策雷达

政策爬虫聚集系统

## 已有功能

- 配置化 HTML 爬虫引擎（HtmlRunner）
  - 支持列表页 + 详情页配置
  - 支持 CSS 选择器提取字段
  - 支持分页（URL 模板方式）
  - 支持按发布日期增量抓取

- 多种数据源执行器
  - HTML - 配置化爬虫
  - RSS - RSS/Atom 订阅
  - HTTP_JSON - JSON API
  - PYTHON - Python 脚本
  - SEARCH - 搜索引擎

- 数据处理管道
  - 去重
  - 标准化
  - 关键词匹配
  - 原始数据保存

- 动态任务调度
  - 从数据库读取数据源配置
  - 动态注册/删除 Cron 任务

## 技术栈

- Java 21
- Spring Boot 3.4
- MyBatis-Plus 3.5.9
- MySQL
- Jsoup

## 快速开始

1. 配置数据库连接（application.yml）
2. 执行 init-db.sql 初始化表
3. 启动应用

## 配置 HTML 爬虫

在数据库配置以下表：
- policy_data_source - 数据源基本信息
- policy_crawl_page - 列表页/详情页配置
- policy_extract_rule - 字段提取规则
- policy_pagination_rule - 分页规则
