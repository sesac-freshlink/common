package com.freshlink.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 보낸 줄·처리 기록 정리 설정. {@code freshlink.messaging.cleanup.*} (v0.4.0)
 *
 * @param enabled            끄면 정리하지 않는다. 기본 켬
 * @param interval           한 회차가 끝나고 다음 회차까지 기다리는 시간. 기본 1시간
 * @param outboxRetention    {@code published = TRUE}이고 {@code published_at}이 이보다 오래된 outbox 행을 지운다. 기본 7일
 * @param processedRetention {@code processed_at}이 이보다 오래된 processed_message 행을 지운다. 기본 30일.
 *                           SQS 본 큐 보관(4일) + DLQ 보관(14일)보다 길어야 한다. 짧으면 DLQ에서 재처리한 쪽지를 새것으로 보고 두 번 반영한다
 * @param batchSize          DELETE 한 번에 지우는 최대 행 수. 기본 1000
 */
@ConfigurationProperties("freshlink.messaging.cleanup")
public record CleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1h") Duration interval,
        @DefaultValue("7d") Duration outboxRetention,
        @DefaultValue("30d") Duration processedRetention,
        @DefaultValue("1000") int batchSize) {

    /** SQS 본 큐 4일 + DLQ 14일(infra modules/messaging 기본값). processed_message는 이보다 오래 남아야 한다 */
    static final Duration MAX_MESSAGE_LIFETIME = Duration.ofDays(18);

    public CleanupProperties {
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("freshlink.messaging.cleanup.interval은 0보다 커야 한다");
        }
        if (outboxRetention.isNegative() || outboxRetention.isZero()) {
            throw new IllegalArgumentException("freshlink.messaging.cleanup.outbox-retention은 0보다 커야 한다");
        }
        if (processedRetention.compareTo(MAX_MESSAGE_LIFETIME) <= 0) {
            throw new IllegalArgumentException("freshlink.messaging.cleanup.processed-retention은 18일(SQS 본 큐 4일 + DLQ 14일)보다 길어야 한다");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("freshlink.messaging.cleanup.batch-size는 1 이상이어야 한다");
        }
    }
}
