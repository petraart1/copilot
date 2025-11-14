package com.copilot.tools.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

@Slf4j
@Component
public class EmailServiceHealthChecker {

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${spring.mail.port:1025}")
    private Integer mailPort;

    @EventListener(ApplicationReadyEvent.class)
    public void checkSmtpConnection() {
        log.info("Проверка подключения к SMTP серверу {}:{}", mailHost, mailPort);
        log.info("Примечание: SMTP используется только для отправки писем на реальные email адреса. " +
                "Для MailSlurp адресов используется MailSlurp API.");
        
        // Проверяем TCP подключение
        boolean tcpConnectionOk = checkTcpConnection(mailHost, mailPort);
        
        if (!tcpConnectionOk) {
            log.warn("⚠️  SMTP сервер {}:{} недоступен по TCP. " +
                    "Письма на реальные email адреса (личные email) не будут отправляться до тех пор, пока SMTP сервер не станет доступен. " +
                    "Письма на MailSlurp адреса будут отправляться через MailSlurp API независимо от состояния SMTP сервера.", 
                    mailHost, mailPort);
            log.warn("Для отправки писем на реальные email адреса настройте SMTP сервер через переменные окружения: " +
                    "MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD");
        } else {
            log.info("✅ TCP подключение к SMTP серверу {}:{} успешно", mailHost, mailPort);
            log.info("SMTP конфигурация: host={}, port={}", mailHost, mailPort);
        }
    }

    private boolean checkTcpConnection(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return true;
        } catch (SocketTimeoutException e) {
            log.debug("SMTP сервер {}:{} не отвечает (таймаут)", host, port);
            return false;
        } catch (Exception e) {
            log.debug("Не удалось подключиться к SMTP серверу {}:{}: {}", host, port, e.getMessage());
            return false;
        }
    }
}

