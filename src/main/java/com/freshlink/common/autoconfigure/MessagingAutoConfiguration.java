package com.freshlink.common.autoconfigure;

import com.freshlink.common.idempotency.ProcessedMessages;
import com.freshlink.common.outbox.OutboxProperties;
import com.freshlink.common.outbox.OutboxPublisher;
import com.freshlink.common.outbox.OutboxWriter;
import com.freshlink.common.tracing.MicrometerOutboxTracing;
import com.freshlink.common.tracing.OutboxTracing;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * {@code freshlink.messaging.enabled=true}일 때만 Outbox·멱등 처리 빈을 만든다. inventory·order만 켠다.
 *
 * <p>필요한 것: DataSource(서비스 DB), {@code SqsTemplate}(Spring Cloud AWS SQS), ShedLock {@code LockProvider} 빈.
 * {@code LockProvider}가 없으면 앱이 뜰 때 실패한다. 조용히 두 파드가 같이 보내는 것보다 낫다.
 *
 * <p>v0.3.0: Micrometer Tracing이 클래스 경로에 있으면(서비스의 {@code spring-boot-starter-zipkin}) Outbox가 추적을 잇는다.
 * 없으면 {@link OutboxTracing#NOOP}.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration"
})
@ConditionalOnProperty(prefix = "freshlink.messaging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
public class MessagingAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class MicrometerTracingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OutboxTracing freshlinkOutboxTracing(ObjectProvider<io.micrometer.tracing.Tracer> tracer,
                                             ObjectProvider<io.micrometer.tracing.propagation.Propagator> propagator) {
            return new MicrometerOutboxTracing(tracer::getIfAvailable, propagator::getIfAvailable);
        }
    }

    @Bean
    public OutboxWriter freshlinkOutboxWriter(DataSource dataSource, ObjectProvider<OutboxTracing> tracing) {
        return new OutboxWriter(new JdbcTemplate(dataSource), tracing.getIfAvailable(() -> OutboxTracing.NOOP));
    }

    @Bean
    public ProcessedMessages freshlinkProcessedMessages(DataSource dataSource) {
        return new ProcessedMessages(new JdbcTemplate(dataSource));
    }

    @Bean
    public OutboxPublisher freshlinkOutboxPublisher(DataSource dataSource, SqsTemplate sqsTemplate,
                                                    ObjectProvider<LockProvider> lockProvider,
                                                    OutboxProperties properties,
                                                    ObjectProvider<OutboxTracing> tracing) {
        LockProvider provider = lockProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("freshlink.messaging.enabled=true에는 ShedLock LockProvider 빈이 필요하다. "
                    + "파드가 여러 개일 때 같은 쪽지를 같이 보내지 않게 한다 (common README 4장)");
        }
        return new OutboxPublisher(new JdbcTemplate(dataSource), sqsTemplate,
                new DefaultLockingTaskExecutor(provider), properties, Clock.systemUTC(),
                tracing.getIfAvailable(() -> OutboxTracing.NOOP));
    }
}
