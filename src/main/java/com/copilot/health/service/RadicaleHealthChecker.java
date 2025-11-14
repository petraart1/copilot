package com.copilot.health.service;

import com.copilot.health.dto.ServiceHealth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Проверка состояния Radicale (CalDAV сервер)
 */
@Slf4j
@Component
public class RadicaleHealthChecker {

    @Value("${calendar.caldav.base-url:http://calendar:5232}")
    private String caldavBaseUrl;

    public ServiceHealth check() {
        long startTime = System.currentTimeMillis();
        log.debug("Проверка доступности Radicale по адресу: {}", caldavBaseUrl);
        
        try {
            // Проверяем доступность Radicale через OPTIONS запрос (CalDAV)
            @SuppressWarnings("deprecation")
            URL url = new URL(caldavBaseUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("OPTIONS");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            log.debug("Radicale ответил с кодом: {} за {}ms", responseCode, responseTime);

            // Radicale должен отвечать на OPTIONS запрос (может вернуть 200, 207, 401, 405)
            // 401 - нормально, означает что сервер работает, но требует авторизацию
            // 405 - метод не разрешен, но сервер доступен
            if (responseCode >= 200 && responseCode < 500) {
                return ServiceHealth.up("Radicale доступен (код: " + responseCode + ")", responseTime);
            } else {
                return ServiceHealth.down("Radicale вернул код: " + responseCode);
            }
        } catch (java.net.ConnectException e) {
            log.warn("Radicale недоступен: Connection refused. URL: {}. Проверьте, запущен ли контейнер calendar и доступен ли он по адресу {}", 
                    caldavBaseUrl, caldavBaseUrl);
            return ServiceHealth.down("Radicale недоступен: Connection refused. URL: " + caldavBaseUrl + 
                    ". Убедитесь, что контейнер calendar запущен и доступен по этому адресу");
        } catch (java.net.UnknownHostException e) {
            log.warn("Radicale недоступен: неизвестный хост '{}'. Проверьте CALDAV_BASE_URL: {}", e.getMessage(), caldavBaseUrl);
            return ServiceHealth.down("Radicale недоступен: неизвестный хост '" + e.getMessage() + 
                    "'. Проверьте CALDAV_BASE_URL (текущее значение: " + caldavBaseUrl + ")");
        } catch (java.net.SocketTimeoutException e) {
            log.warn("Radicale недоступен: таймаут подключения. URL: {}", caldavBaseUrl);
            return ServiceHealth.down("Radicale недоступен: таймаут подключения. URL: " + caldavBaseUrl);
        } catch (Exception e) {
            log.error("Ошибка при проверке Radicale (URL: {}): {}", caldavBaseUrl, e.getMessage(), e);
            return ServiceHealth.down("Radicale недоступен: " + e.getMessage() + " (URL: " + caldavBaseUrl + ")");
        }
    }
}

