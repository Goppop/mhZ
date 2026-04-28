package com.policyradar.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.policyradar.persistence.entity.PolicyNotification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PolicyNotificationMapper extends BaseMapper<PolicyNotification> {
}