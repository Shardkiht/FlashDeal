package com.flashdeal.riskguard.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * risk-guard 独立数据源 DAO，只查必要的 2 个字段，不依赖 flashdeal-core 的任何类
 */
@Repository
public class AccountInfoDao {

    private final JdbcTemplate jdbcTemplate;

    public AccountInfoDao(@Qualifier("riskGuardJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int queryAccountAgeDays(Long userId) {
        String sql = "SELECT DATEDIFF(NOW(), create_time) FROM user WHERE id = ?";
        Integer days = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return days == null ? 365 : days;
    }

    public int queryOrderCount(Long userId) {
        String sql = "SELECT COUNT(*) FROM `voucher_order` WHERE user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count == null ? 0 : count;
    }
}
