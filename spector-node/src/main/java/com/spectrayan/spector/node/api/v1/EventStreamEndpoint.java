package com.spectrayan.spector.node.api.v1;

import java.time.Duration;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.linecorp.armeria.common.sse.ServerSentEvent;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.ProducesEventStream;

import com.spectrayan.spector.events.NotificationTransport;
import com.spectrayan.spector.events.SubscriberIdentity;
import com.spectrayan.spector.node.api.ApiModule;
import com.spectrayan.spector.node.event.SpectorEvent;
import com.spectrayan.spector.node.event.SpectorEventBus;
import com.spectrayan.spector.node.exception.ApiExceptionHandler;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * SSE event stream endpoint — clients subscribe to live Spector events.
 *
 * <p>Uses the {@link NotificationTransport} SPI for scope-aware delivery.
 * Each SSE client is registered with a {@link SubscriberIdentity} built from
 * the authentication context, so only events matching their scope are delivered.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   GET /api/v1/events                       — all events (scope-filtered per subscriber)
 *   GET /api/v1/events?filter=search,document — only search + document event categories
 *   GET /api/v1/events?filter=cluster         — only cluster events
 * </pre>
 *
 * <h3>Scope Filtering</h3>
 * <p>The transport automatically filters events by {@link com.spectrayan.spector.events.NotificationScope}.
 * For example, an ingestion progress event scoped to User A will only be delivered
 * to User A's SSE connection, not to User B.</p>
 *
 * <h3>Category Filtering</h3>
 * <p>In addition to scope filtering, clients can request only specific event
 * categories via the {@code filter} query parameter (e.g., {@code cortex,ingestion}).
 * This is applied as a client-side preference on top of scope-based delivery.</p>
 */
@ExceptionHandler(ApiExceptionHandler.class)
public class EventStreamEndpoint implements ApiModule {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventStreamEndpoint.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SpectorEventBus eventBus;

    public EventStreamEndpoint(SpectorEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String pathPrefix() { return ""; }

    @Get("/events")
    @ProducesEventStream
    public Publisher<ServerSentEvent> eventStream(
            @Param("filter") String filter) {

        Set<String> categories = parseFilter(filter);

        // Build subscriber identity from authentication context.
        // In enterprise mode, resolves userId/tenantId from JWT.
        // In OSS mode, returns anonymous identity → receives only Global-scoped events + unscoped.
        SubscriberIdentity identity = resolveSubscriberIdentity();

        Sinks.Many<ServerSentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        // Subscribe through the notification transport — scope filtering is automatic.
        NotificationTransport.Subscription subscription = eventBus.subscribe(identity, event -> {
            // Apply category filter (client preference) on top of scope filtering
            if (!categories.isEmpty() && !matchesFilter(event, categories)) {
                return;
            }
            try {
                String data = MAPPER.writeValueAsString(event);
                ServerSentEvent sse = ServerSentEvent.builder()
                        .event(event.eventType())
                        .data(data)
                        .build();
                sink.tryEmitNext(sse);
            } catch (Exception e) {
                log.warn("[SSE-DISPATCH] serialization FAILED for '{}': {}", event.eventType(), e.getMessage());
            }
        });

        // Send heartbeat every 30s to keep connection alive
        Flux<ServerSentEvent> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.builder()
                        .event("heartbeat")
                        .data("{}")
                        .build());

        return Flux.merge(sink.asFlux(), heartbeat)
                .doOnCancel(subscription::cancel)
                .doOnTerminate(subscription::cancel);
    }

    /**
     * Resolves the subscriber's identity from the authentication context.
     *
     * <p>Uses reflection to access {@code AuthContextHolder} from the enterprise
     * module without creating a compile-time dependency. In OSS mode (no auth),
     * returns an anonymous identity that only receives broadcast events.</p>
     *
     * <p>In enterprise mode, the identity includes userId and tenantId so that
     * scope filtering delivers user-specific and tenant-specific events correctly.</p>
     */
    private static SubscriberIdentity resolveSubscriberIdentity() {
        try {
            Class<?> holderClass = Class.forName("com.spectrayan.spector.management.auth.AuthContextHolder");
            Object ctx = holderClass.getMethod("current").invoke(null);
            if (ctx != null) {
                String userId = invokeStringMethod(ctx, "userId");
                String tenantId = invokeStringMethod(ctx, "tenantId");
                if (userId != null) {
                    return SubscriberIdentity.ofUser(userId, tenantId);
                }
            }
        } catch (ClassNotFoundException e) {
            // OSS mode — no auth module
        } catch (Exception e) {
            log.debug("[SSE] Could not resolve subscriber identity: {}", e.getMessage());
        }
        return SubscriberIdentity.anonymous();
    }

    private static String invokeStringMethod(Object obj, String methodName) {
        try {
            Object result = obj.getClass().getMethod(methodName).invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> parseFilter(String filter) {
        if (filter == null || filter.isBlank()) return Set.of();
        return Set.of(filter.toLowerCase().split(","));
    }

    private static boolean matchesFilter(SpectorEvent event, Set<String> categories) {
        String eventType = event.eventType(); // e.g., "search.completed"
        String category = eventType.contains(".")
                ? eventType.substring(0, eventType.indexOf('.'))
                : eventType;
        return categories.contains(category);
    }
}
