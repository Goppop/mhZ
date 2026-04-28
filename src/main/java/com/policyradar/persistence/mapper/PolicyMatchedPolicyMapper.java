package com.policyradar.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.policyradar.persistence.entity.PolicyMatchedPolicy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PolicyMatchedPolicyMapper extends BaseMapper<PolicyMatchedPolicy> {
}