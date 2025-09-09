package com.stocksanalyses.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.timescale")
    public DataSourceProperties timescaleProps(){ return new DataSourceProperties(); }

    @Bean
    public DataSource timescaleDataSource(@Qualifier("timescaleProps") DataSourceProperties props){
        return props.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate timescaleJdbcTemplate(@Qualifier("timescaleDataSource") DataSource ds){ return new JdbcTemplate(ds); }

    @Bean
    @ConfigurationProperties("spring.datasource.clickhouse")
    public DataSourceProperties clickhouseProps(){ return new DataSourceProperties(); }

    @Bean
    public DataSource clickhouseDataSource(@Qualifier("clickhouseProps") DataSourceProperties props){
        return props.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate clickhouseJdbcTemplate(@Qualifier("clickhouseDataSource") DataSource ds){ return new JdbcTemplate(ds); }
}


