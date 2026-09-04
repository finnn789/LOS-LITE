package org.project.loslite.component;

import org.project.loslite.dto.WorkflowExecutionEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Subscribe SATU koneksi STOMP-over-WebSocket bersama ke WORKFLOW (topic
 * {@code /topic/workflow/{instanceId}}) - satu {@link StompSession} dipakai ulang untuk
 * semua businessKey/instanceId yang di-watch (pola sama seperti
 * {@code websocketService.js} milik bpmn-ui: satu client dibagi, subscribe per topic).
 * <p>
 * BEST-EFFORT SEPENUHNYA seperti {@link WorkflowAppClient} - gagal connect/subscribe
 * TIDAK PERNAH melempar ke pemanggil (cukup log warn). Ini cuma jalur LIVE update;
 * {@link WorkflowAppClient#startProcess} tetap menulis hasil trigger awal lewat REST
 * duluan, jadi domain LOS-LITE tidak pernah tersandera nyala/matinya WebSocket ini.
 */
@Component
public class WorkflowSocketClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSocketClient.class);
    private static final long CONNECT_TIMEOUT_SECONDS = 5;

    private final WebSocketStompClient stompClient;
    private final String wsUrl;
    private final boolean enabled;
    private final WebSocketHttpHeaders handshakeHeaders;

    private volatile CompletableFuture<StompSession> sessionFuture;

    public WorkflowSocketClient(
            WebSocketStompClient workflowStompClient,
            @Value("${workflow-app.ws-url}") String wsUrl,
            @Value("${workflow-app.enabled}") boolean enabled,
            @Value("${workflow-app.username}") String username,
            @Value("${workflow-app.password}") String password) {
        this.stompClient = workflowStompClient;
        this.wsUrl = wsUrl;
        this.enabled = enabled;

        // Handshake WebSocket /ws WORKFLOW juga wajib HTTP Basic Auth, sama seperti
        // endpoint REST /api/** (lihat dokumentasi API WORKFLOW) - dipasang sekali di sini,
        // dipakai ulang tiap connectAsync() lewat session().
        this.handshakeHeaders = new WebSocketHttpHeaders();
        this.handshakeHeaders.setBasicAuth(username, password);
    }

    /**
     * Subscribe live event untuk satu workflow instance. Dipanggil sekali per instance
     * (lihat {@link WorkflowAppClient#startProcess}) - berlangganan seumur hidup proses
     * JVM ini (tidak ada unsubscribe eksplisit; instance yang sudah
     * COMPLETED/FAILED tetap "terlanggan" tapi tidak akan terima event baru lagi).
     */
    public void watch(String instanceId, Consumer<WorkflowExecutionEventPayload> onEvent) {
        if (!enabled) {
            return;
        }

        try {
            StompSession session = session();

            session.subscribe("/topic/workflow/" + instanceId, new StompFrameHandler() {
                @Override
                @NonNull
                public Type getPayloadType(@NonNull StompHeaders headers) {
                    return WorkflowExecutionEventPayload.class;
                }

                @Override
                public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                    try {
                        onEvent.accept((WorkflowExecutionEventPayload) payload);
                    } catch (Exception e) {
                        log.warn("Gagal proses event WORKFLOW untuk instanceId={}", instanceId, e);
                    }
                }
            });

            log.info("Subscribe live event WORKFLOW untuk instanceId={}", instanceId);
        } catch (Exception e) {
            log.warn("Gagal subscribe WebSocket WORKFLOW untuk instanceId={} - update live tidak aktif untuk instance ini, jalur REST (findOpenTasks/completeTask) tetap bisa dipakai manual", instanceId, e);
        }
    }

    private synchronized StompSession session() throws Exception {
        if (sessionFuture == null || sessionFuture.isCompletedExceptionally()) {
            sessionFuture = stompClient.connectAsync(wsUrl, handshakeHeaders, new StompSessionHandlerAdapter() {
                @Override
                public void handleTransportError(@NonNull StompSession session, @NonNull Throwable exception) {
                    log.warn("Koneksi WebSocket ke WORKFLOW ({}) terputus", wsUrl, exception);
                }
            });
        }

        return sessionFuture.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
