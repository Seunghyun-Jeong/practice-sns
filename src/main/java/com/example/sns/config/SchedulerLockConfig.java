package com.example.sns.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @SchedulerLock이 쓰는 잠금을 DB(shedlock 표)에 둔다. 서버들이 같은 DB를 보므로 따로 띄울 것이 없다.
 *
 * usingDbTime: 잠금 만료를 각 서버의 시계가 아니라 DB 시계로 잰다.
 * 서버끼리 시계가 몇 초 어긋나면 만료 전에 다른 서버가 잠금을 가져갈 수 있기 때문이다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
