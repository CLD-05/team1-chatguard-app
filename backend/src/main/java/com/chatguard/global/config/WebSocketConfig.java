package com.chatguard.global.config;

import com.chatguard.domain.chat.ws.ChatWebSocketHandler;
import com.chatguard.global.auth.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.WebSocketHandler;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
            .addInterceptors(webSocketAuthInterceptor)
            .setHandshakeHandler(new DefaultHandshakeHandler() {
                @Override
                protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler wsHandler) {
                    if (!requestedProtocols.isEmpty()) {
                        return requestedProtocols.get(0);
                    }
                    return super.selectProtocol(requestedProtocols, wsHandler);
                }
            })
            .setAllowedOriginPatterns("*");
    }
}
