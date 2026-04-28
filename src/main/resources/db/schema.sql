-- MySQL Schema for Policy Radar
-- 创建数据库（如果不存在）
-- CREATE DATABASE IF NOT EXISTS policy_radar DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE policy_radar;

-- 1. 数据源配置表
CREATE TABLE IF NOT EXISTS policy_data_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '数据源名称',
    type VARCHAR(50) NOT NULL COMMENT '类型：API/RSS/WEBMAGIC/PYTHON',
    config TEXT COMMENT 'JSON格式配置',
    script_path VARCHAR(500) COMMENT 'Python脚本路径（type=PYTHON时使用）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    cron_expr VARCHAR(100) COMMENT 'Cron表达式',
    last_published_at DATETIME COMMENT '上次抓取的最大发布日期（增量抓取游标）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled),
    INDEX idx_type (type),
    INDEX idx_last_published_at (last_published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源配置表';

-- 2. 关键词订阅表
CREATE TABLE IF NOT EXISTS policy_keyword (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '订阅名称',
    keyword VARCHAR(500) COMMENT '主关键词',
    include_any TEXT COMMENT '任意包含这些词（逗号分隔）',
    include_all TEXT COMMENT '必须包含所有词（逗号分隔）',
    exclude_any TEXT COMMENT '排除包含这些词（逗号分隔）',
    match_fields VARCHAR(200) DEFAULT 'title,summary,content' COMMENT '匹配字段',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    search_enabled TINYINT(1) DEFAULT 0 COMMENT '是否参与主动搜索（0=仅事后过滤，1=同时驱动检索）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键词订阅表';

-- 3. 政策文档表
CREATE TABLE IF NOT EXISTS policy_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(500) COMMENT '标题',
    url VARCHAR(500) COMMENT '原文链接',
    source VARCHAR(200) COMMENT '来源名称',
    issuing_agency VARCHAR(200) COMMENT '发布机构',
    document_number VARCHAR(200) COMMENT '文号',
    publish_date DATE COMMENT '发布日期',
    summary TEXT COMMENT '摘要',
    content LONGTEXT COMMENT '正文内容',
    metadata JSON COMMENT '灵活扩展字段',
    content_hash VARCHAR(100) COMMENT '内容哈希（去重用）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_url (url),
    INDEX idx_source (source),
    INDEX idx_publish_date (publish_date),
    INDEX idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='政策文档表';

-- 4. 推送目标配置表
CREATE TABLE IF NOT EXISTS policy_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) COMMENT '名称',
    type VARCHAR(50) NOT NULL COMMENT '类型：PUSHPLUS/EMAIL/DINGTALK',
    config TEXT COMMENT 'JSON配置（token、邮箱等）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推送目标配置表';

-- 5. 抓取任务日志表
CREATE TABLE IF NOT EXISTS policy_crawl_task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    data_source_id BIGINT COMMENT '关联数据源ID',
    status VARCHAR(50) COMMENT '状态：RUNNING/SUCCESS/FAILED',
    fetched_count INT DEFAULT 0 COMMENT '抓取条数',
    error_msg TEXT COMMENT '错误信息',
    started_at DATETIME COMMENT '开始时间',
    ended_at DATETIME COMMENT '结束时间',
    INDEX idx_data_source_id (data_source_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抓取任务日志表';

-- 6. 原始内容存储表
CREATE TABLE IF NOT EXISTS policy_raw_payload (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_log_id BIGINT NOT NULL COMMENT '关联抓取任务日志ID',
    data_source_id BIGINT NOT NULL COMMENT '关联数据源ID',
    payload_type VARCHAR(20) COMMENT '内容类型：JSON/HTML/RSS_XML',
    payload LONGTEXT COMMENT '原始内容（太大时可考虑文件系统存储）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_log_id (task_log_id),
    INDEX idx_data_source_id (data_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原始内容存储表';

-- ============================================================
-- 数据初始化：开启"数据要素"关键词的主动搜索 + 注册 gov_cn 检索源
-- （已有关键词 id=1 时执行；如 id 不同请自行调整）
-- UPDATE policy_keyword SET search_enabled = 1 WHERE name = '数据要素';
--
-- INSERT INTO policy_data_source (name, type, config, enabled, cron_expr)
-- VALUES (
--   '政府网站内搜-数据要素',
--   'SEARCH',
--   '{"provider":"gov_cn","keyword_ids":[1],"since_days":7,"max_per_keyword":30}',
--   1,
--   '0 0 8,20 * * ?'
-- );
-- ============================================================

-- 7. 候选 URL 队列表（全网爬取）
CREATE TABLE IF NOT EXISTS policy_url_frontier (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    url VARCHAR(1000) NOT NULL COMMENT '候选 URL',
    url_hash CHAR(40) NOT NULL COMMENT 'SHA1(url)，用于去重',
    data_source_id BIGINT COMMENT '来自哪个 type=SEARCH 数据源',
    keyword_id BIGINT COMMENT '来自哪条关键词订阅',
    provider VARCHAR(50) COMMENT '检索器名称，如 gov_cn',
    title_snippet VARCHAR(500) COMMENT '搜索结果标题片段',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/FETCHING/FETCHED/FAILED/SKIPPED',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    last_error VARCHAR(500) COMMENT '最后一次错误信息',
    discovered_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
    fetched_at DATETIME COMMENT '抓取完成时间',
    UNIQUE KEY uk_url_hash (url_hash),
    INDEX idx_status (status),
    INDEX idx_keyword_id (keyword_id),
    INDEX idx_data_source_id (data_source_id),
    INDEX idx_discovered_at (discovered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候选 URL 队列';

-- 8. 关键词匹配结果表
CREATE TABLE IF NOT EXISTS policy_matched_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL COMMENT '关联文档ID',
    keyword_id BIGINT NOT NULL COMMENT '关联关键词ID',
    matched_keyword VARCHAR(500) COMMENT '匹配到的关键词',
    matched_fields VARCHAR(200) COMMENT '匹配到的字段',
    notified TINYINT(1) DEFAULT 0 COMMENT '是否已推送',
    matched_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id),
    INDEX idx_keyword_id (keyword_id),
    INDEX idx_notified (notified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键词匹配结果表';
