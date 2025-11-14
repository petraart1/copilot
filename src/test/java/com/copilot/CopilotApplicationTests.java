package com.copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-testing-purposes-only-min-32-chars",
        "jwt.issuer=com.copilot",
        "jwt.access-token-ttl=15m",
        "jwt.refresh-token-ttl=7d",
        "spring.redis.host=localhost",
        "spring.redis.port=6379",
        "mailslurp.api-key=test-key",
        "calendar.caldav.base-url=http://localhost:5232",
        "meetings.jitsi.base-url=https://meet.jit.si"
})
class CopilotApplicationTests {

    @Test
    void contextLoads() {
    }

}
