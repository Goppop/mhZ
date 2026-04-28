-- =================================================================
-- Policy Radar unified database initialization
-- Includes DDL + seed data. Prefer this file for fresh environments.
-- =================================================================

-- CREATE DATABASE IF NOT EXISTS policy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE policy;

-- 1. Data source registry
CREATE TABLE IF NOT EXISTS policy_data_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '数据源名称',
    type VARCHAR(50) NOT NULL COMMENT '类型：RSS/HTTP_JSON/HTML/PY_SCRIPT/SEARCH',
    config TEXT COMMENT 'JSON配置；HTML 类型不使用该字段，改由结构化配置表驱动',
    script_path VARCHAR(500) COMMENT 'Python脚本路径（type=PY_SCRIPT时使用）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    cron_expr VARCHAR(100) COMMENT 'Cron表达式',
    last_published_at DATETIME COMMENT '上次抓取的最大发布日期（增量抓取游标）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_policy_data_source_name (name),
    INDEX idx_enabled (enabled),
    INDEX idx_type (type),
    INDEX idx_last_published_at (last_published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源配置表';

-- 2. Keyword subscriptions
CREATE TABLE IF NOT EXISTS policy_keyword (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '订阅名称',
    keyword VARCHAR(500) COMMENT '主关键词',
    include_any TEXT COMMENT '任意包含这些词（逗号分隔）',
    include_all TEXT COMMENT '必须包含所有词（逗号分隔）',
    exclude_any TEXT COMMENT '排除包含这些词（逗号分隔）',
    match_fields VARCHAR(200) DEFAULT 'title,summary,content' COMMENT '匹配字段',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    search_enabled TINYINT(1) DEFAULT 0 COMMENT '是否参与主动搜索',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_policy_keyword_name (name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键词订阅表';

-- 3. Policy documents
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
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_url (url),
    INDEX idx_source (source),
    INDEX idx_publish_date (publish_date),
    INDEX idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='政策文档表';

-- 4. Notification targets
CREATE TABLE IF NOT EXISTS policy_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) COMMENT '名称',
    type VARCHAR(50) NOT NULL COMMENT '类型：PUSHPLUS/EMAIL/DINGTALK',
    config TEXT COMMENT 'JSON配置（token、邮箱等）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推送目标配置表';

-- 5. Crawl task logs
CREATE TABLE IF NOT EXISTS policy_crawl_task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    data_source_id BIGINT COMMENT '关联数据源ID',
    status VARCHAR(50) COMMENT '状态：RUNNING/SUCCESS/FAILED',
    fetched_count INT DEFAULT 0 COMMENT '抓取条数',
    error_msg TEXT COMMENT '错误信息',
    started_at DATETIME COMMENT '开始时间',
    ended_at DATETIME COMMENT '结束时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_data_source_id (data_source_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抓取任务日志表';

-- 6. Raw payload snapshots
CREATE TABLE IF NOT EXISTS policy_raw_payload (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_log_id BIGINT NOT NULL COMMENT '关联抓取任务日志ID',
    data_source_id BIGINT NOT NULL COMMENT '关联数据源ID',
    payload_type VARCHAR(20) COMMENT '内容类型：JSON/HTML/RSS_XML',
    payload LONGTEXT COMMENT '原始内容（太大时可考虑文件系统存储）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_log_id (task_log_id),
    INDEX idx_data_source_id (data_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原始内容存储表';

-- 7. URL frontier
CREATE TABLE IF NOT EXISTS policy_url_frontier (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    url VARCHAR(1000) NOT NULL COMMENT '候选 URL',
    url_hash CHAR(40) NOT NULL COMMENT 'SHA1(url)，用于去重',
    data_source_id BIGINT COMMENT '来自哪个 type=SEARCH 的 policy_data_source',
    keyword_id BIGINT COMMENT '来自哪条关键词订阅',
    provider VARCHAR(50) COMMENT '检索器名称，如 gov_cn',
    title_snippet VARCHAR(500) COMMENT '搜索结果标题片段',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/FETCHING/FETCHED/FAILED/SKIPPED',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    last_error VARCHAR(500) COMMENT '最后一次错误信息',
    discovered_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
    fetched_at DATETIME COMMENT '抓取完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_url_hash (url_hash),
    INDEX idx_status (status),
    INDEX idx_keyword_id (keyword_id),
    INDEX idx_data_source_id (data_source_id),
    INDEX idx_discovered_at (discovered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候选 URL 队列';

-- 8. Keyword match results
CREATE TABLE IF NOT EXISTS policy_matched_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL COMMENT '关联文档ID',
    keyword_id BIGINT NOT NULL COMMENT '关联关键词ID',
    matched_keyword VARCHAR(500) COMMENT '匹配到的关键词',
    matched_fields VARCHAR(200) COMMENT '匹配到的字段',
    notified TINYINT(1) DEFAULT 0 COMMENT '是否已推送',
    matched_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id),
    INDEX idx_keyword_id (keyword_id),
    INDEX idx_notified (notified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键词匹配结果表';

-- 9. Crawl page configuration
CREATE TABLE IF NOT EXISTS policy_crawl_page (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    data_source_id BIGINT NOT NULL COMMENT '关联数据源ID',
    page_role VARCHAR(20) NOT NULL COMMENT 'LIST/DETAIL',
    name VARCHAR(200) COMMENT '页面配置名称',
    url VARCHAR(1000) COMMENT '列表页 URL；详情页通常为空，由列表页字段 url 提供',
    item_selector VARCHAR(500) COMMENT 'LIST 页面用于遍历 item 的 CSS 选择器',
    request_method VARCHAR(20) DEFAULT 'GET' COMMENT '请求方法，第一版仅实现 GET',
    headers TEXT COMMENT '预留：请求头 JSON',
    timeout_ms INT DEFAULT 15000 COMMENT '请求超时时间',
    sort_order INT DEFAULT 0 COMMENT '排序',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_data_source_id (data_source_id),
    INDEX idx_page_role (page_role),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='爬虫页面配置表';

-- 10. CSS extraction rules
CREATE TABLE IF NOT EXISTS policy_extract_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    page_id BIGINT NOT NULL COMMENT '关联 policy_crawl_page.id',
    field_name VARCHAR(100) NOT NULL COMMENT 'RawDoc 字段名：title/url/publishDate/summary/content/issuingAgency/documentNumber',
    scope VARCHAR(20) DEFAULT 'ITEM' COMMENT 'ITEM/PAGE',
    selector VARCHAR(500) COMMENT 'CSS 选择器；为空时表示使用当前根节点',
    value_type VARCHAR(20) DEFAULT 'TEXT' COMMENT 'TEXT/ATTR/HTML/CONST/REGEX',
    attr_name VARCHAR(100) COMMENT 'value_type=ATTR 时的属性名，如 href/title',
    const_value TEXT COMMENT 'value_type=CONST 时的常量值',
    regex_pattern VARCHAR(500) COMMENT '正则提取表达式，默认取第一个分组或完整匹配',
    date_format VARCHAR(100) COMMENT '日期格式，如 yyyy-MM-dd/yyyy年M月d日/yyyy/MM/dd',
    required TINYINT(1) DEFAULT 0 COMMENT '是否必填',
    fallback_group VARCHAR(100) DEFAULT 'default' COMMENT '同字段多规则兜底分组',
    sort_order INT DEFAULT 0 COMMENT '同字段内尝试顺序',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_page_id (page_id),
    INDEX idx_field_name (field_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段提取规则表';

-- 11. Pagination configuration
CREATE TABLE IF NOT EXISTS policy_pagination_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    page_id BIGINT NOT NULL COMMENT '关联 policy_crawl_page.id',
    mode VARCHAR(30) DEFAULT 'NONE' COMMENT 'NONE/URL_TEMPLATE/NEXT_SELECTOR',
    url_template VARCHAR(1000) COMMENT 'URL_TEMPLATE 模式，如 https://example/list_{page}.html',
    next_selector VARCHAR(500) COMMENT 'NEXT_SELECTOR 模式的下一页链接选择器',
    start_page INT DEFAULT 1 COMMENT '起始页码',
    max_pages INT DEFAULT 1 COMMENT '最大页数',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_page_id (page_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分页规则表';

-- =================================================================
-- Seed keywords
-- =================================================================

INSERT INTO policy_keyword
    (name, keyword, include_any, include_all, exclude_any, match_fields, enabled, search_enabled)
VALUES
    ('数据要素', '数据要素', '数据要素,公共数据,数据资源,数据资产,数据产权', NULL, '个人数据,隐私保护培训,数据安全事故', 'title,summary,content,issuing_agency', 1, 1),
    ('数字经济', '数字经济', '数字经济,数字化转型,数字产业,数字基础设施,新型数字', NULL, '数字阅读,数字出版,游戏', 'title,summary,content', 1, 1),
    ('人工智能', '人工智能', '人工智能,AI治理,大模型,生成式AI,算法治理,智能计算', NULL, '人工智能大赛报名,竞赛通知', 'title,summary,content', 1, 1),
    ('平台经济', '平台经济', '平台经济,互联网平台,反垄断,不正当竞争,平台监管,算法推荐', NULL, NULL, 'title,summary,content', 1, 0),
    ('数据安全', '数据安全', '数据安全,网络安全,关键信息基础设施,等级保护,个人信息保护,MLPS', NULL, '数据安全事故报道,黑客', 'title,summary,content', 1, 1)
ON DUPLICATE KEY UPDATE
    keyword = VALUES(keyword),
    include_any = VALUES(include_any),
    include_all = VALUES(include_all),
    exclude_any = VALUES(exclude_any),
    match_fields = VALUES(match_fields),
    enabled = VALUES(enabled),
    search_enabled = VALUES(search_enabled);

-- Search data source
INSERT INTO policy_data_source (name, type, config, enabled, cron_expr)
VALUES (
    '政府网站内搜-全关键词',
    'SEARCH',
    '{"provider":"gov_cn","keyword_ids":[1,2,3,5],"since_days":7,"max_per_keyword":30}',
    1,
    '0 0 8,20 * * ?'
) ON DUPLICATE KEY UPDATE
    config = VALUES(config),
    enabled = VALUES(enabled),
    cron_expr = VALUES(cron_expr);

-- Example HTML data source: NDRC notices
INSERT INTO policy_data_source (name, type, config, enabled, cron_expr)
VALUES (
    '发改委通知公告',
    'HTML',
    NULL,
    1,
    '0 0 8 * * ?'
) ON DUPLICATE KEY UPDATE
    type = VALUES(type),
    enabled = VALUES(enabled),
    cron_expr = VALUES(cron_expr);

DELETE FROM policy_extract_rule
WHERE page_id IN (
    SELECT id FROM policy_crawl_page
    WHERE data_source_id = (SELECT id FROM policy_data_source WHERE name = '发改委通知公告')
);

DELETE FROM policy_pagination_rule
WHERE page_id IN (
    SELECT id FROM policy_crawl_page
    WHERE data_source_id = (SELECT id FROM policy_data_source WHERE name = '发改委通知公告')
);

DELETE FROM policy_crawl_page
WHERE data_source_id = (SELECT id FROM policy_data_source WHERE name = '发改委通知公告');

INSERT INTO policy_crawl_page
    (data_source_id, page_role, name, url, item_selector, request_method, timeout_ms, sort_order, enabled)
SELECT id, 'LIST', '发改委通知公告列表页', 'https://www.ndrc.gov.cn/xxgk/zcfb/tz/', 'ul.u-list > li', 'GET', 15000, 1, 1
FROM policy_data_source
WHERE name = '发改委通知公告'
UNION ALL
SELECT id, 'DETAIL', '发改委通知公告详情页', NULL, NULL, 'GET', 15000, 2, 1
FROM policy_data_source
WHERE name = '发改委通知公告';

INSERT INTO policy_extract_rule
    (page_id, field_name, scope, selector, value_type, attr_name, regex_pattern, date_format, required, fallback_group, sort_order, enabled)
SELECT p.id, 'title', 'ITEM', 'a[href]', 'ATTR', 'title', NULL, NULL, 1, 'default', 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST'
UNION ALL
SELECT p.id, 'title', 'ITEM', 'a[href]', 'TEXT', NULL, NULL, NULL, 1, 'default', 2, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST'
UNION ALL
SELECT p.id, 'url', 'ITEM', 'a[href]', 'ATTR', 'href', NULL, NULL, 1, 'default', 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST'
UNION ALL
SELECT p.id, 'publishDate', 'ITEM', 'span', 'TEXT', NULL, NULL, 'yyyy/MM/dd', 0, 'default', 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST'
UNION ALL
SELECT p.id, 'title', 'PAGE', 'h1, div.article-title', 'TEXT', NULL, NULL, NULL, 0, 'default', 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'DETAIL'
UNION ALL
SELECT p.id, 'content', 'PAGE', 'div.TRS_Editor, div.article-content', 'TEXT', NULL, NULL, NULL, 0, 'default', 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'DETAIL'
UNION ALL
SELECT p.id, 'publishDate', 'PAGE', 'div.article-time span, span.time', 'TEXT', NULL, '(\\d{4}[年/-]\\d{1,2}[月/-]\\d{1,2}日?)', NULL, 0, 'default', 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'DETAIL'
UNION ALL
SELECT p.id, 'issuingAgency', 'PAGE', 'div.article-source, span.source', 'TEXT', NULL, NULL, NULL, 0, 'default', 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'DETAIL';

INSERT INTO policy_pagination_rule
    (page_id, mode, start_page, max_pages, enabled)
SELECT p.id, 'NONE', 1, 1, 1
FROM policy_crawl_page p JOIN policy_data_source ds ON ds.id = p.data_source_id
WHERE ds.name = '发改委通知公告' AND p.page_role = 'LIST';
