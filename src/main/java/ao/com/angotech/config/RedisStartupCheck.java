package ao.com.angotech.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisStartupCheck implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RedisStartupCheck.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisStartupCheck(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) {
        String key = "startup::ping";
        String value = "pong";

        redisTemplate.opsForValue().set(key, value);
        Object result = redisTemplate.opsForValue().get(key);

        logger.info("Redis startup check - key: {}, value: {}", key, result);
    }

}
