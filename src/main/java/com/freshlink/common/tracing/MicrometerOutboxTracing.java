package com.freshlink.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Micrometer Tracing(서비스의 {@code spring-boot-starter-zipkin}이 Brave로 넣는다)으로 만든 {@link OutboxTracing}.
 *
 * <p>Tracer·Propagator는 처음 쓸 때 꺼낸다. 자동 설정 순서와 상관없이 동작하게 하려는 것이다. 빈이 없으면 {@link OutboxTracing#NOOP}처럼 동작한다.
 */
public class MicrometerOutboxTracing implements OutboxTracing {

    private final Supplier<Tracer> tracer;
    private final Supplier<Propagator> propagator;

    public MicrometerOutboxTracing(Supplier<Tracer> tracer, Supplier<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public String currentTraceParent() {
        Tracer t = tracer.get();
        Span span = t == null ? null : t.currentSpan();
        return span == null ? null : format(span.context());
    }

    @Override
    public void inSpan(String traceParent, String name, Consumer<String> action) {
        Tracer t = tracer.get();
        Propagator p = propagator.get();
        if (traceParent == null || t == null || p == null) {
            action.accept(traceParent);
            return;
        }
        Span span = p.extract(Map.of(TRACEPARENT, traceParent), Map::get)
                .name(name)
                .kind(Span.Kind.PRODUCER)
                .start();
        try (Tracer.SpanInScope ignored = t.withSpan(span)) {
            action.accept(format(span.context()));
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /** W3C traceparent. traceId가 64비트(16자)면 앞을 0으로 채워 32자로 만든다 */
    static String format(TraceContext context) {
        String traceId = context.traceId();
        if (traceId.length() < 32) {
            traceId = "0".repeat(32 - traceId.length()) + traceId;
        }
        return "00-" + traceId + "-" + context.spanId() + "-" + (Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }
}
