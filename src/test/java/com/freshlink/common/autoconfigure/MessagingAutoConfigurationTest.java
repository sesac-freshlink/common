package com.freshlink.common.autoconfigure;

import com.freshlink.common.idempotency.ProcessedMessages;
import com.freshlink.common.outbox.OutboxProperties;
import com.freshlink.common.outbox.OutboxPublisher;
import com.freshlink.common.outbox.OutboxWriter;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessagingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(SqsTemplate.class, () -> mock(SqsTemplate.class))
            // 테스트 중 발행기가 목 DataSource를 건드리지 않게 회차 간격을 길게 둔다
            .withPropertyValues("freshlink.messaging.outbox.publish-interval=1h");

    @Test
    @DisplayName("freshlink.messaging.enabled가 없으면 빈이 하나도 생기지 않는다")
    void disabledByDefault() {
        runner.withBean(LockProvider.class, () -> config -> Optional.empty())
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(OutboxWriter.class)
                        .doesNotHaveBean(OutboxPublisher.class)
                        .doesNotHaveBean(ProcessedMessages.class)
                        .doesNotHaveBean(OutboxProperties.class));
    }

    @Test
    @DisplayName("켜면 OutboxWriter·OutboxPublisher·ProcessedMessages가 생기고 설정 값을 읽는다")
    void enabled() {
        runner.withBean(LockProvider.class, () -> config -> Optional.empty())
                .withPropertyValues("freshlink.messaging.enabled=true", "freshlink.messaging.outbox.batch-size=20")
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(OutboxWriter.class)
                            .hasSingleBean(OutboxPublisher.class)
                            .hasSingleBean(ProcessedMessages.class);
                    OutboxProperties properties = context.getBean(OutboxProperties.class);
                    assertThat(properties.batchSize()).isEqualTo(20);
                    assertThat(properties.publishInterval()).isEqualTo(Duration.ofHours(1));
                    assertThat(context.getBean(OutboxPublisher.class).isRunning()).isTrue();
                });
    }

    @Test
    @DisplayName("기본값은 1초, 100개")
    void defaults() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(SqsTemplate.class, () -> mock(SqsTemplate.class))
                .withBean(LockProvider.class, () -> config -> Optional.empty())
                .withPropertyValues("freshlink.messaging.enabled=true")
                .run(context -> {
                    OutboxProperties properties = context.getBean(OutboxProperties.class);
                    assertThat(properties.publishInterval()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.batchSize()).isEqualTo(100);
                });
    }

    @Test
    @DisplayName("켰는데 LockProvider가 없으면 앱이 뜰 때 실패한다")
    void failsWithoutLockProvider() {
        runner.withPropertyValues("freshlink.messaging.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("LockProvider"));
    }
}
