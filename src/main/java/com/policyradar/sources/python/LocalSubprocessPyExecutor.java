package com.policyradar.sources.python;

import com.policyradar.sources.RawDoc;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 本地子进程 Python 执行器
 *
 * 使用 Java 的 ProcessBuilder 执行 Python 脚本
 * 通过 JSON Lines 协议通信
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "policy-radar.python.runtime",
        havingValue = "subprocess",
        matchIfMissing = true
)
public class LocalSubprocessPyExecutor implements PyExecutor {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService timeoutExecutor;

    public LocalSubprocessPyExecutor() {
        this.objectMapper = new ObjectMapper();
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "python-executor-timeout")
        );
    }

    @Override
    public List<RawDoc> invoke(String scriptPath, List<String> args, int timeoutSec) {
        List<RawDoc> results = new ArrayList<>();
        final Process[] processWrapper = new Process[1];
        Future<?> timeoutFuture = null;

        try {
            // 构建命令
            List<String> cmd = new ArrayList<>();
            cmd.add("python3"); // 也可配置
            cmd.add(scriptPath);
            cmd.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);

            processWrapper[0] = pb.start();
            log.debug("启动 Python 脚本: {} ({})", scriptPath, String.join(" ", args));

            // 超时处理
            timeoutFuture = timeoutExecutor.schedule(() -> {
                log.warn("Python 脚本超时，强制终止: {}", scriptPath);
                if (processWrapper[0] != null) {
                    processWrapper[0].destroyForcibly();
                }
            }, timeoutSec, TimeUnit.SECONDS);

            // 读取 stdout（JSON Lines）
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(processWrapper[0].getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        RawDoc doc = objectMapper.readValue(line, RawDoc.class);
                        results.add(doc);
                    } catch (Exception e) {
                        log.warn("无法解析 Python 输出: {}", line);
                    }
                }
            }

            // 读取 stderr（日志）
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(processWrapper[0].getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("[LOG]")) {
                        log.debug("PY: {}", line.substring(5).trim());
                    } else if (line.startsWith("[WARN]")) {
                        log.warn("PY: {}", line.substring(6).trim());
                    } else if (line.startsWith("[ERROR]")) {
                        log.error("PY: {}", line.substring(7).trim());
                    } else {
                        log.debug("PY: {}", line);
                    }
                }
            }

            int exitCode = processWrapper[0].waitFor();
            if (exitCode != 0) {
                log.error("Python 脚本执行失败 (code {}): {}", exitCode, scriptPath);
            }

            log.debug("Python 脚本完成: {}, 获取 {} 条文档",
                    scriptPath, results.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Python 脚本执行被中断: {}", scriptPath, e);
            if (processWrapper[0] != null) {
                processWrapper[0].destroyForcibly();
            }
        } catch (IOException e) {
            log.error("Python 脚本执行失败: {}", scriptPath, e);
            if (processWrapper[0] != null) {
                processWrapper[0].destroyForcibly();
            }
        } catch (Exception e) {
            log.error("Python 脚本执行异常: {}", scriptPath, e);
            if (processWrapper[0] != null) {
                processWrapper[0].destroyForcibly();
            }
        } finally {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(true);
            }
            if (processWrapper[0] != null && processWrapper[0].isAlive()) {
                processWrapper[0].destroyForcibly();
            }
        }

        return results;
    }
}