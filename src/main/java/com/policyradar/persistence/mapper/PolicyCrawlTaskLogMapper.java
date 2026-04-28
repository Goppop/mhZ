package com.policyradar.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.policyradar.persistence.entity.PolicyCrawlTaskLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PolicyCrawlTaskLogMapper extends BaseMapper<PolicyCrawlTaskLog> {
}