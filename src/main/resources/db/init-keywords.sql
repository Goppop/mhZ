-- =================================================================
-- 关键词订阅初始化脚本
-- 执行前请确认数据库已建表（schema.sql 已执行）
-- =================================================================

-- ★ 关键词组设计原则：
--   keyword     = 最核心的词（用于站内搜的 query）
--   include_any = 任意命中一个才算匹配（扩大召回）
--   include_all = 必须同时包含这些词（缩小噪声）
--   exclude_any = 命中任一就排除（过滤无关内容）
--   search_enabled = 1 表示该组也驱动主动检索

-- -----------------------------------------------------------------
-- 组 1：数据要素（核心领域）
-- -----------------------------------------------------------------
INSERT INTO policy_keyword
    (name, keyword, include_any, include_all, exclude_any, match_fields, enabled, search_enabled)
VALUES (
    '数据要素',
    '数据要素',
    '数据要素,公共数据,数据资源,数据资产,数据产权',
    NULL,
    '个人数据,隐私保护培训,数据安全事故',  -- 排除：安全/培训等噪声
    'title,summary,content,issuing_agency',
    1, 1
) ON DUPLICATE KEY UPDATE
    include_any = VALUES(include_any),
    exclude_any = VALUES(exclude_any),
    search_enabled = 1;

-- -----------------------------------------------------------------
-- 组 2：数字经济
-- -----------------------------------------------------------------
INSERT INTO policy_keyword
    (name, keyword, include_any, include_all, exclude_any, match_fields, enabled, search_enabled)
VALUES (
    '数字经济',
    '数字经济',
    '数字经济,数字化转型,数字产业,数字基础设施,新型数字',
    NULL,
    '数字阅读,数字出版,游戏',
    'title,summary,content',
    1, 1
) ON DUPLICATE KEY UPDATE
    include_any = VALUES(include_any),
    search_enabled = 1;

-- -----------------------------------------------------------------
-- 组 3：人工智能政策
-- -----------------------------------------------------------------
INSERT INTO policy_keyword
    (name, keyword, include_any, include_all, exclude_any, match_fields, enabled, search_enabled)
VALUES (
    '人工智能',
    '人工智能',
    '人工智能,AI治理,大模型,生成式AI,算法治理,智能计算',
    NULL,
    '人工智能大赛报名,竞赛通知',
    'title,summary,content',
    1, 1
) ON DUPLICATE KEY UPDATE
    include_any = VALUES(include_any),
    search_enabled = 1;

-- -----------------------------------------------------------------
-- 组 4：平台经济 / 反垄断（强监管方向）
-- -----------------------------------------------------------------
INSERT INTO policy_keyword
    (name, keyword, include_any, include_all, exclude_any, match_fields, enabled, search_enabled)
VALUES (
    '平台经济',
    '平台经济',
    '平台经济,互联网平台,反垄断,不正当竞争,平台监管,算法推荐',
    NULL,
    NULL,
    'title,summary,content',
    1, 0   -- search_enabled=0：先只做事后过滤，范围偏宽
) ON DUPLICATE KEY UPDATE
    include_any = VALUES(include_any);

-- -----------------------------------------------------------------
-- 组 5：网络安全 / 数据安全（合规方向）
-- -----------------------------------------------------------------
INSERT INTO policy_keyword
    (name, keyword, include_any, include_all, exclude_any, match_fields, enabled, search_enabled)
VALUES (
    '数据安全',
    '数据安全',
    '数据安全,网络安全,关键信息基础设施,等级保护,个人信息保护,MLPS',
    NULL,
    '数据安全事故报道,黑客',
    'title,summary,content',
    1, 1
) ON DUPLICATE KEY UPDATE
    include_any = VALUES(include_any),
    search_enabled = 1;

-- =================================================================
-- 注册检索源（数据源）
-- 说明：keyword_ids 里的数字对应上面插入的关键词 id，
--       如果你的库是全新的，id 从 1 开始；若已有数据请先查询：
--       SELECT id, name FROM policy_keyword;
-- =================================================================

-- 政府网站内搜（每天 8:00 和 20:00 执行，覆盖所有 search_enabled=1 的关键词）
INSERT INTO policy_data_source (name, type, config, enabled, cron_expr)
VALUES (
    '政府网站内搜-全关键词',
    'SEARCH',
    '{"provider":"gov_cn","keyword_ids":[1,2,3,5],"since_days":7,"max_per_keyword":30}',
    1,
    '0 0 8,20 * * ?'
) ON DUPLICATE KEY UPDATE config = VALUES(config);

-- 验证插入结果
SELECT id, name, keyword, include_any, search_enabled FROM policy_keyword;
SELECT id, name, type, enabled, cron_expr FROM policy_data_source;
