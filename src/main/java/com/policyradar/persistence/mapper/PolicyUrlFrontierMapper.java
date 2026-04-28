package com.policyradar.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.policyradar.persistence.entity.PolicyUrlFrontier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PolicyUrlFrontierMapper extends BaseMapper<PolicyUrlFrontier> {

    /**
     * 拉取一批 PENDING 状态的候选 URL，乐观锁方式更新为 FETCHING
     */
    @Select("SELECT * FROM policy_url_frontier WHERE status = 'PENDING' ORDER BY discovered_at ASC LIMIT #{limit}")
    List<PolicyUrlFrontier> selectPending(@Param("limit") int limit);

    @Update("UPDATE policy_url_frontier SET status = 'FETCHING' WHERE id = #{id} AND status = 'PENDING'")
    int markFetching(@Param("id") Long id);

    @Update("UPDATE policy_url_frontier SET status = 'FETCHED', fetched_at = NOW() WHERE id = #{id}")
    int markFetched(@Param("id") Long id);

    @Update("UPDATE policy_url_frontier SET status = 'SKIPPED', last_error = #{reason} WHERE id = #{id}")
    int markSkipped(@Param("id") Long id, @Param("reason") String reason);

    @Update("UPDATE policy_url_frontier SET status = CASE WHEN retry_count >= #{maxRetry} THEN 'FAILED' ELSE 'PENDING' END, " +
            "retry_count = retry_count + 1, last_error = #{error} WHERE id = #{id}")
    int markFailedOrRetry(@Param("id") Long id, @Param("error") String error, @Param("maxRetry") int maxRetry);

    @Select("SELECT COUNT(*) FROM policy_url_frontier WHERE url_hash = #{urlHash}")
    int countByUrlHash(@Param("urlHash") String urlHash);
}
