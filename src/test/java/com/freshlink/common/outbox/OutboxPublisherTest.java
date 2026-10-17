package com.freshlink.common.outbox;

import com.freshlink.common.message.command.ReserveMessage;
import com.freshlink.common.message.result.ReserveFailReason;
import com.freshlink.common.message.result.ReserveFailedMessage;
import com.freshlink.common.support.TestDatabase;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 SqsTemplate에 AWS SDK 클라이언트만 목으로 넣는다. SQS로 나가는 요청(본문, 그룹 ID, 중복 제거 ID)을 그대로 확인한다.
 */
class OutboxPublisherTest {

    private static final String FIFO = "dev-inventory-commands.fifo";
    private static final String STANDARD = "dev-inventory-results";

    private final JdbcTemplate jdbc = TestDatabase.jdbcTemplate();
    private final TransactionTemplate tx = TestDatabase.transactionTemplate();
    private final OutboxWriter writer = new OutboxWriter(jdbc);
    private final OutboxProperties properties = new OutboxProperties(Duration.ofSeconds(1), 100);

    private SqsAsyncClient sqs;

    @BeforeEach
    void setUp() {
        TestDatabase.resetSchema();
        sqs = mock(SqsAsyncClient.class);
        when(sqs.getQueueUrl(any(GetQueueUrlRequest.class))).thenAnswer(inv -> {
            GetQueueUrlRequest req = inv.getArgument(0);
            return CompletableFuture.completedFuture(
                    GetQueueUrlResponse.builder().queueUrl("https://sqs.test/" + req.queueName()).build());
        });
        GetQueueAttributesResponse noAttributes = GetQueueAttributesResponse.builder().build();
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(noAttributes));
        when(sqs.getQueueAttributes(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(noAttributes));
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenAnswer(inv -> sent());
    }

    @Test
    @DisplayName("한 회차에 저장 순서대로 보내고, 그룹 ID·중복 제거 ID가 맞고, published = true")
    void publishInOrder() {
        ReserveMessage first = ReserveMessage.of(10L, 1L, 2L, 3);
        ReserveMessage second = ReserveMessage.of(11L, 2L, 5L, 1);
        ReserveFailedMessage result = ReserveFailedMessage.of(12L, 1L, 2L, ReserveFailReason.OUT_OF_STOCK, 0);
        tx.executeWithoutResult(status -> {
            writer.save(FIFO, first);
            writer.save(FIFO, second);
            writer.save(STANDARD, result);
        });

        int sent = publisher(jdbcLock()).publishOnce();

        assertThat(sent).isEqualTo(3);
        List<SendMessageRequest> requests = sentRequests(3);
        assertThat(requests).extracting(SendMessageRequest::queueUrl).containsExactly(
                "https://sqs.test/" + FIFO, "https://sqs.test/" + FIFO, "https://sqs.test/" + STANDARD);
        assertThat(requests.get(0).messageBody()).isEqualTo(payloadOf(first.messageId()));
        assertThat(requests.get(0).messageGroupId()).isEqualTo("1");
        assertThat(requests.get(0).messageDeduplicationId()).isEqualTo(first.messageId());
        assertThat(requests.get(1).messageGroupId()).isEqualTo("2");
        assertThat(requests.get(1).messageDeduplicationId()).isEqualTo(second.messageId());
        assertThat(requests.get(2).messageBody()).isEqualTo(payloadOf(result.messageId()));
        assertThat(requests.get(2).messageGroupId()).isNull();
        assertThat(requests.get(2).messageDeduplicationId()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published = TRUE AND published_at IS NOT NULL",
                Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("본문은 저장한 JSON 문자열 그대로 나간다 (따옴표로 한 번 더 감싸지 않는다)")
    void bodyIsRawJson() {
        tx.executeWithoutResult(status -> writer.save(FIFO, ReserveMessage.of(10L, 1L, 2L, 3)));

        publisher(jdbcLock()).publishOnce();

        assertThat(sentRequests(1).get(0).messageBody()).startsWith("{").contains("\"type\":\"RESERVE\"");
    }

    @Test
    @DisplayName("세 행 중 두 번째에서 실패하면 첫 번째만 published, 세 번째는 시도하지 않고, 다음 회차에 두 번째부터 다시 보낸다")
    void stopOnFailure() {
        ReserveMessage first = ReserveMessage.of(10L, 1L, 2L, 3);
        ReserveMessage second = ReserveMessage.of(11L, 1L, 2L, 3);
        ReserveMessage third = ReserveMessage.of(12L, 1L, 2L, 3);
        tx.executeWithoutResult(status -> {
            writer.save(FIFO, first);
            writer.save(FIFO, second);
            writer.save(FIFO, third);
        });
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenAnswer(inv -> sent())
                .thenReturn(CompletableFuture.failedFuture(SdkClientException.create("일시 장애")))
                .thenAnswer(inv -> sent());
        OutboxPublisher publisher = publisher(jdbcLock());

        assertThat(publisher.publishOnce()).isEqualTo(1);
        assertThat(sentRequests(2)).extracting(SendMessageRequest::messageDeduplicationId)
                .containsExactly(first.messageId(), second.messageId());
        assertThat(publishedIds()).containsExactly(first.messageId());

        assertThat(publisher.publishOnce()).isEqualTo(2);
        assertThat(sentRequests(4)).extracting(SendMessageRequest::messageDeduplicationId)
                .containsExactly(first.messageId(), second.messageId(), second.messageId(), third.messageId());
        assertThat(publishedIds()).containsExactly(first.messageId(), second.messageId(), third.messageId());
    }

    @Test
    @DisplayName("잠금을 다른 파드가 잡고 있으면 그 회차는 아무것도 보내지 않는다")
    void lockHeldElsewhere() {
        tx.executeWithoutResult(status -> writer.save(FIFO, ReserveMessage.of(10L, 1L, 2L, 3)));
        jdbc.update("INSERT INTO shedlock (name, lock_until, locked_at, locked_by) "
                + "VALUES (?, DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE), UTC_TIMESTAMP(3), 'other-pod')",
                OutboxPublisher.LOCK_NAME);

        assertThat(publisher(jdbcLock()).publishOnce()).isZero();

        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
        assertThat(publishedIds()).isEmpty();
    }

    @Test
    @DisplayName("잠금을 못 잡으면 DB도 읽지 않는다")
    void noLockNoWork() {
        LockProvider neverLocks = config -> Optional.empty();
        tx.executeWithoutResult(status -> writer.save(FIFO, ReserveMessage.of(10L, 1L, 2L, 3)));

        assertThat(publisher(neverLocks).publishOnce()).isZero();
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("batch-size만큼만 꺼낸다")
    void batchSize() {
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) {
                writer.save(FIFO, ReserveMessage.of((long) i, 1L, 2L, 1));
            }
        });
        OutboxPublisher publisher = new OutboxPublisher(jdbc, SqsTemplate.newTemplate(sqs),
                new DefaultLockingTaskExecutor(jdbcLock()), new OutboxProperties(Duration.ofSeconds(1), 2),
                Clock.systemUTC());

        assertThat(publisher.publishOnce()).isEqualTo(2);
        assertThat(publisher.publishOnce()).isEqualTo(1);
    }

    private OutboxPublisher publisher(LockProvider lockProvider) {
        return new OutboxPublisher(jdbc, SqsTemplate.newTemplate(sqs),
                new DefaultLockingTaskExecutor(lockProvider), properties, Clock.systemUTC());
    }

    private LockProvider jdbcLock() {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc).usingDbTime().build());
    }

    private List<SendMessageRequest> sentRequests(int expected) {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs, times(expected)).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private String payloadOf(String messageId) {
        return jdbc.queryForObject("SELECT payload FROM outbox WHERE message_id = ?", String.class, messageId);
    }

    private List<String> publishedIds() {
        return jdbc.queryForList("SELECT message_id FROM outbox WHERE published = TRUE ORDER BY id", String.class);
    }

    private static CompletableFuture<SendMessageResponse> sent() {
        return CompletableFuture.completedFuture(
                SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build());
    }
}
