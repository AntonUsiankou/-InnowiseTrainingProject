package com.ausiankou.listener;

import com.ausiankou.service.TokenBlacklistService;
import com.ausiankou.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserStatusUpdateListener implements MessageListener {

    private final UserCacheService userCacheService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            log.info("Received user status update: {}", payload);

            String[] parts = payload.split(":");
            if (parts.length == 2) {
                Long userId = Long.parseLong(parts[0]);
                boolean active = Boolean.parseBoolean(parts[1]);

                userCacheService.refreshUserStatus(userId, active);

                if (!active) {
                    log.info("User deactivated, tokens will be invalidated: userId={}", userId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing user status update: {}", e.getMessage());
        }
    }
}
