package com.freshlink.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Outbox 발행기 설정. {@code freshlink.messaging.outbox.*}
 *
 * @param publishInterval 한 회차가 끝나고 다음 회차까지 기다리는 시간. 기본 1초
 * @param batchSize       한 회차에 꺼내는 최대 행 수. 기본 100
 */
@ConfigurationProperties("freshlink.messaging.outbox")
public record OutboxProperties(
        @DefaultValue("1s") Duration publishInterval,
        @DefaultValue("100") int batchSize) {

    public OutboxProperties {
        if (publishInterval.isNegative() || publishInterval.isZero()) {
            throw new IllegalArgumentException("freshlink.messaging.outbox.publish-interval은 0보다 커야 한다");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("freshlink.messaging.outbox.batch-size는 1 이상이어야 한다");
        }
    }
}
