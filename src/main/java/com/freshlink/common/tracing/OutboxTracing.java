package com.freshlink.common.tracing;

import java.util.function.Consumer;

/**
 * Outbox를 거쳐도 추적(traceId)이 SQS 너머까지 이어지게 하는 다리 (v0.3.0, 할 일 16).
 *
 * <ul>
 *   <li>저장할 때: {@link #currentTraceParent()}로 지금 요청의 추적을 W3C {@code traceparent} 문자열로 받아 {@code outbox.trace_parent}에 적는다.</li>
 *   <li>보낼 때: 발행기는 별도 스레드라 요청의 추적이 없다. {@link #inSpan}이 저장해 둔 값을 부모로 구간을 열고,
 *       그 구간의 {@code traceparent}를 SQS 메시지 속성으로 붙여 보낸다. 받는 쪽은
 *       {@code spring.cloud.aws.sqs.observation-enabled=true}면 그 속성을 읽어 같은 추적을 잇는다.</li>
 * </ul>
 *
 * <p>Micrometer Tracing이 없는 서비스에서는 {@link #NOOP}이 쓰여 아무것도 하지 않는다(행의 {@code trace_parent}는 NULL).
 */
public interface OutboxTracing {

    /** SQS 메시지 속성 이름 (W3C Trace Context) */
    String TRACEPARENT = "traceparent";

    OutboxTracing NOOP = new OutboxTracing() {
        @Override
        public String currentTraceParent() {
            return null;
        }

        @Override
        public void inSpan(String traceParent, String name, Consumer<String> action) {
            action.accept(traceParent);
        }
    };

    /** 지금 스레드의 추적을 {@code 00-<traceId 32자>-<spanId 16자>-<01|00>}로. 추적 중이 아니면 null */
    String currentTraceParent();

    /**
     * {@code traceParent}를 부모로 한 구간 안에서 {@code action}을 실행한다. {@code action}은 메시지에 붙일
     * {@code traceparent}(이 구간의 값, 부모가 없으면 null)를 받는다. {@code action}이 던진 예외는 그대로 다시 던진다.
     */
    void inSpan(String traceParent, String name, Consumer<String> action);
}
