package com.policyradar.sources;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SourceRunner 注册表
 *
 * 管理和维护所有 SourceRunner 实例的单例
 * 替换原来的 FetchStrategyFactory，使用 type() 作为注册表 key
 */
@Component
public class RunnerRegistry {

    private final Map<String, SourceRunner> runnerMap = new ConcurrentHashMap<>();

    /**
     * 构造函数，自动注入所有 SourceRunner 实例
     *
     * @param runners 所有 SourceRunner 实例列表
     */
    public RunnerRegistry(List<SourceRunner> runners) {
        for (SourceRunner runner : runners) {
            runnerMap.put(runner.type(), runner);
        }
    }

    /**
     * 根据 type 获取 Runner 实例
     *
     * @param type 数据源类型
     * @return SourceRunner 实例
     */
    public SourceRunner getRunner(String type) {
        SourceRunner runner = runnerMap.get(type);
        if (runner == null) {
            throw new IllegalArgumentException("不支持的数据源类型: " + type);
        }
        return runner;
    }

    /**
     * 获取所有支持的 type 列表
     *
     * @return 所有支持的 type 列表
     */
    public List<String> getAllTypes() {
        return runnerMap.keySet().stream().collect(Collectors.toList());
    }

    /**
     * 获取所有 SourceRunner 实例
     *
     * @return 所有 SourceRunner 实例列表
     */
    public List<SourceRunner> getAllRunners() {
        return List.copyOf(runnerMap.values());
    }

    /**
     * 判断是否支持某种类型的 Runner
     *
     * @param type 数据源类型
     * @return 是否支持
     */
    public boolean supports(String type) {
        return runnerMap.containsKey(type);
    }
}