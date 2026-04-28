package com.policyradar.pipeline;

import com.policyradar.sources.RawDoc;
import com.policyradar.persistence.entity.PolicyRawPayload;
import com.policyradar.persistence.mapper.PolicyRawPayloadMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 原始内容存储组件
 *
 * 将 RawDoc 转换为 PolicyRawPayload 并保存到数据库
 * 用于故障排查和数据回放
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawPayloadWriter {

    private final PolicyRawPayloadMapper rawPayloadMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存原始内容
     *
     * @param taskLogId 任务日志 ID
     * @param dataSourceId 数据源 ID
     * @param rawDocs 原始文档列表
     */
    public void writeRawPayload(Long taskLogId, Long dataSourceId, List<RawDoc> rawDocs) {
        if (rawDocs == null || rawDocs.isEmpty()) {
            log.debug("没有原始文档需要保存，taskLogId: {}", taskLogId);
            return;
        }

        try {
            List<PolicyRawPayload> payloads = rawDocs.stream()
                    .map(doc -> convertToRawPayload(taskLogId, dataSourceId, doc))
                    .collect(Collectors.toList());

            // 批量插入（逐个插入）
            if (!payloads.isEmpty()) {
                for (PolicyRawPayload payload : payloads) {
                    try {
                        rawPayloadMapper.insert(payload);
                    } catch (Exception e) {
                        log.error("插入原始内容失败: {}", e.getMessage(), e);
                    }
                }
                log.info("原始内容保存完成，taskLogId: {}, 共 {} 条",
                        taskLogId, payloads.size());
            }

        } catch (Exception e) {
            log.error("原始内容保存失败，taskLogId: {}", taskLogId, e);
        }
    }

    /**
     * 将 RawDoc 转换为 PolicyRawPayload
     */
    private PolicyRawPayload convertToRawPayload(Long taskLogId, Long dataSourceId, RawDoc rawDoc) {
        PolicyRawPayload payload = new PolicyRawPayload();
        payload.setTaskLogId(taskLogId);
        payload.setDataSourceId(dataSourceId);
        payload.setPayloadType("JSON"); // 统一为 JSON

        // 将 RawDoc 转换为 JSON 字符串
        try {
            String json = objectMapper.writeValueAsString(rawDoc);
            payload.setPayload(json);
        } catch (Exception e) {
            log.error("RawDoc 转 JSON 失败: {}", rawDoc.getUrl(), e);
            payload.setPayload("{}");
        }

        return payload;
    }

    /**
     * 保存单个原始文档
     */
    public void writeSinglePayload(Long taskLogId, Long dataSourceId, RawDoc rawDoc) {
        try {
            PolicyRawPayload payload = convertToRawPayload(taskLogId, dataSourceId, rawDoc);
            rawPayloadMapper.insert(payload);
        } catch (Exception e) {
            log.error("保存单个原始文档失败: {}", rawDoc.getUrl(), e);
        }
    }

    /**
     * 删除过期的原始内容（用于清理）
     *
     * @param keepDays 保留天数
     * @return 删除的条数
     */
    public int deleteExpiredPayload(int keepDays) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(keepDays);

        // 构建查询条件
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PolicyRawPayload> queryWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.lt("created_at", cutoffTime);

        int count = rawPayloadMapper.delete(queryWrapper);
        log.info("删除了 {} 条过期的原始内容（保留 {} 天）", count, keepDays);
        return count;
    }
}