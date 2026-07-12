package com.flashdeal.riskguard.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * risk-guard 独立数据源配置，与 flashdeal-core 的 DataSource 互不感知
 */
@Configuration
public class RiskGuardDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "risk-guard.datasource")
    public DataSource riskGuardDataSource() {
        return new com.zaxxer.hikari.HikariDataSource();
    }

    @Bean
    public JdbcTemplate riskGuardJdbcTemplate(@Qualifier("riskGuardDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
