package com.policyradar.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.policyradar.persistence.entity.PolicyRawPayload;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PolicyRawPayloadMapper extends BaseMapper<PolicyRawPayload> {
}