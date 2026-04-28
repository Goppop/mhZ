package com.policyradar.sources.runner;

import com.policyradar.persistence.entity.PolicyDataSource;
import com.policyradar.sources.FetchContext;
import com.policyradar.sources.RawDoc;
import com.policyradar.sources.SourceRunner;
import com.policyradar.sources.python.PyExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Python 脚本源执行器
 *
 * 委托给 PyExecutor 执行 Python 脚本
 * 支持自定义脚本和参数配置
 */
@Slf4j
@Component
public class PyScriptRunner implements SourceRunner {

    private final PyExecutor pyExecutor;

    public PyScriptRunner(PyExecutor pyExecutor) {
        this.pyExecutor = pyExecutor;
    }

    @Override
    public String type() {
        return "PY_SCRIPT";
    }

    @Override
    public List<RawDoc> fetch(PolicyDataSource dataSource, FetchContext context) {
        PyScriptConfig config = parseConfig(dataSource.getConfig());

        // 构建参数
        List<String> args = new ArrayList<>(config.getArgs());
        if (context.getLastPublishedAt() != null) {
            args.add("--since=" + context.getLastPublishedAt().toString());
        }
        if (context.isDebugMode()) {
            args.add("--debug");
        }
        if (context.getTaskLogId() != null) {
            args.add("--task-id=" + context.getTaskLogId());
        }

        // 调用 PyExecutor
        List<RawDoc> docs = pyExecutor.invoke(
                config.getScriptPath(),
                args,
                config.getTimeoutSec()
        );

        log.debug("PY_SCRIPT 源 {} 抓取完成，获取到 {} 条文档",
                dataSource.getName(), docs.size());

        return docs;
    }

    private PyScriptConfig parseConfig(String config) {
        Map<String, Object> map = parseJson(config);

        // 解析参数
        List<String> args = new ArrayList<>();
        Object argsObj = map.get("args");
        if (argsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> argList = (List<Object>) argsObj;
            for (Object arg : argList) {
                args.add(String.valueOf(arg));
            }
        }

        return PyScriptConfig.builder()
                .scriptPath(String.valueOf(map.getOrDefault("script", "")))
                .args(args)
                .timeoutSec(parseInt(map.get("timeout_sec"), 60))
                .build();
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> map = new HashMap<>();
        try {
            String content = json.trim().substring(1, json.trim().length() - 1);
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] kv = pair.trim().split(":");
                if (kv.length >= 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败，返回空配置", e);
        }
        return map;
    }

    @lombok.Data
    @lombok.Builder
    private static class PyScriptConfig {
        private String scriptPath;
        private List<String> args = new ArrayList<>();
        private int timeoutSec = 60;
        private String runtime = "subprocess";
    }
}