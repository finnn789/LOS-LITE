package org.project.loslite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Bean {@link WebSocketStompClient} milik {@link org.project.loslite.component.WorkflowSocketClient}
 * - CLIENT STOMP-over-WebSocket ke WORKFLOW (bukan server; LOS-LITE tidak expose endpoint
 * WS sendiri, lihat pom.xml untuk kenapa spring-boot-starter-websocket tetap dipakai
 * meski cuma sisi client). Message converter Jackson supaya frame STOMP MESSAGE
 * (JSON {@code WorkflowExecutionEvent} dari WORKFLOW) otomatis di-deserialize ke
 * {@link org.project.loslite.dto.WorkflowExecutionEventPayload} - lihat
 * StompFrameHandler#getPayloadType di WorkflowSocketClient. Heartbeat SENGAJA tidak
 * dikonfigurasi (tidak ada TaskScheduler) - WORKFLOW sendiri broadcast heart-beat:0,0
 * (nonaktif), jadi tidak ada yang perlu dijaga di sisi client ini.
 */
@Configuration
public class WorkflowSocketConfig {

    @Bean
    public WebSocketStompClient workflowStompClient() {

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        return stompClient;
    }
}
