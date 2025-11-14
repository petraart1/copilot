package com.copilot.tools.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String LOCK_PREFIX = "lock:";
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(10);

    public boolean tryLock(String lockKey, Duration ttl) {
        String key = LOCK_PREFIX + lockKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key,
                "locked",
                ttl.toMillis(),
                TimeUnit.MILLISECONDS
        );
        
        boolean result = Boolean.TRUE.equals(acquired);
        if (result) {
            log.debug("Блокировка получена: {}", lockKey);
        } else {
            log.debug("Блокировка уже занята: {}", lockKey);
        }
        return result;
    }

    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_LOCK_TTL);
    }

    public void releaseLock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        redisTemplate.delete(key);
        log.debug("Блокировка освобождена: {}", lockKey);
    }
}

