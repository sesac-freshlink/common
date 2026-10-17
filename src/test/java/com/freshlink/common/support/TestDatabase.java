package com.freshlink.common.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;

/**
 * 테스트용 MySQL. 기본은 Testcontainers MySQL 8.4(CI에는 Docker가 있다).
 * Docker가 없으면 이미 떠 있는 MySQL을 환경 변수 FRESHLINK_TEST_MYSQL_URL·_USERNAME·_PASSWORD로 준다 (README 6장).
 */
public final class TestDatabase {

    private static DataSource dataSource;

    private TestDatabase() {
    }

    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            String url = System.getenv("FRESHLINK_TEST_MYSQL_URL");
            if (url != null && !url.isBlank()) {
                dataSource = new DriverManagerDataSource(url,
                        System.getenv("FRESHLINK_TEST_MYSQL_USERNAME"), System.getenv("FRESHLINK_TEST_MYSQL_PASSWORD"));
            } else {
                MySQLContainer mysql = new MySQLContainer("mysql:8.4");
                mysql.start();
                dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            }
        }
        return dataSource;
    }

    /** 표를 지우고 새로 만든다 (테스트마다 빈 표) */
    public static void resetSchema() {
        new ResourceDatabasePopulator(new ClassPathResource("messaging-schema.sql")).execute(dataSource());
    }

    public static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource());
    }

    public static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource()));
    }
}
