package com.freshlink.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrometerOutboxTracingTest {

    private static final String W3C = "00-[0-9a-f]{32}-[0-9a-f]{16}-01";

    private final BraveTestTracing brave = new BraveTestTracing();
    private final MicrometerOutboxTracing tracing = brave.outboxTracing();

    @AfterEach
    void close() {
        brave.close();
    }

    @Test
    @DisplayName("추적 중이 아니면 null, 추적 중이면 지금 구간의 traceparent")
    void currentTraceParent() {
        assertThat(tracing.currentTraceParent()).isNull();

        Span span = brave.tracer.nextSpan().name("request").start();
        try (Tracer.SpanInScope ignored = brave.tracer.withSpan(span)) {
            assertThat(tracing.currentTraceParent())
                    .matches(W3C)
                    .isEqualTo("00-" + span.context().traceId() + "-" + span.context().spanId() + "-01");
        } finally {
            span.end();
        }
    }

    @Test
    @DisplayName("저장해 둔 traceparent를 부모로 구간을 열어, 같은 traceId·새 spanId를 넘긴다")
    void inSpanContinuesTrace() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String parent = "00-" + traceId + "-00f067aa0ba902b7-01";
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<String> currentInside = new AtomicReference<>();

        tracing.inSpan(parent, "outbox publish", tp -> {
            seen.set(tp);
            currentInside.set(tracing.currentTraceParent());
        });

        assertThat(seen.get()).matches(W3C).startsWith("00-" + traceId + "-").isNotEqualTo(parent);
        assertThat(currentInside.get()).isEqualTo(seen.get());
        assertThat(tracing.currentTraceParent()).isNull();
    }

    @Test
    @DisplayName("저장해 둔 값이 없으면 구간 없이 null을 넘긴다")
    void inSpanWithoutParent() {
        AtomicReference<String> seen = new AtomicReference<>("x");

        tracing.inSpan(null, "outbox publish", seen::set);

        assertThat(seen.get()).isNull();
    }

    @Test
    @DisplayName("안에서 난 예외는 그대로 나가고 구간은 닫힌다")
    void inSpanRethrows() {
        assertThatThrownBy(() -> tracing.inSpan("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", "outbox publish",
                tp -> {
                    throw new IllegalStateException("전송 실패");
                })).isInstanceOf(IllegalStateException.class).hasMessage("전송 실패");
        assertThat(tracing.currentTraceParent()).isNull();
    }

    @Test
    @DisplayName("Tracer가 없으면 아무것도 하지 않는다")
    void noTracer() {
        MicrometerOutboxTracing none = new MicrometerOutboxTracing(() -> null, () -> null);
        AtomicReference<String> seen = new AtomicReference<>();

        none.inSpan("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", "outbox publish", seen::set);

        assertThat(none.currentTraceParent()).isNull();
        assertThat(seen.get()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }
}
