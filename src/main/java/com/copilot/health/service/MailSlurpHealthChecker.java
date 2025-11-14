package com.copilot.health.service;

import com.copilot.health.dto.ServiceHealth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Проверка состояния MailSlurp API
 */
@Slf4j
@Component
public class MailSlurpHealthChecker {

    @Value("${mailslurp.api-key:}")
    private String apiKey;

    private static final String MAILSLURP_API_URL = "https://api.mailslurp.com/health";

    public ServiceHealth check() {
        if (apiKey == null || apiKey.isEmpty()) {
            return ServiceHealth.down("API ключ MailSlurp не настроен");
        }

        long startTime = System.currentTimeMillis();
        try {
            // Проверяем доступность API через простой HTTP запрос
            @SuppressWarnings("deprecation")
            URL url = new URL(MAILSLURP_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("x-api-key", apiKey);

            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // HTTP_UNAUTHORIZED тоже означает, что API доступен, просто нужен валидный ключ
                return ServiceHealth.up("MailSlurp API доступен", responseTime);
            } else {
                return ServiceHealth.down("MailSlurp API вернул код: " + responseCode);
            }
        } catch (Exception e) {
            log.error("Ошибка при проверке MailSlurp: {}", e.getMessage());
            return ServiceHealth.down("MailSlurp API недоступен: " + e.getMessage());
        }
    }
}

