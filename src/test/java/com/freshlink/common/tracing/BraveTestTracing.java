package com.freshlink.common.tracing;

import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.brave.bridge.W3CPropagation;
import io.micrometer.tracing.propagation.Propagator;

import java.util.List;

/** 서비스와 같은 모양(Brave + W3C traceparent, 128비트 traceId)의 테스트용 추적기 */
public final class BraveTestTracing implements AutoCloseable {

    public final Tracer tracer;
    public final Propagator propagator;
    private final Tracing tracing;

    public BraveTestTracing() {
        ThreadLocalCurrentTraceContext context = ThreadLocalCurrentTraceContext.newBuilder().build();
        BraveBaggageManager baggage = new BraveBaggageManager();
        tracing = Tracing.newBuilder()
                .currentTraceContext(context)
                .propagationFactory(new W3CPropagation(baggage, List.of()))
                .sampler(Sampler.ALWAYS_SAMPLE)
                .traceId128Bit(true)
                .supportsJoin(false)
                .build();
        tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(context), baggage);
        propagator = new BravePropagator(tracing);
    }

    public MicrometerOutboxTracing outboxTracing() {
        return new MicrometerOutboxTracing(() -> tracer, () -> propagator);
    }

    @Override
    public void close() {
        tracing.close();
    }
}
