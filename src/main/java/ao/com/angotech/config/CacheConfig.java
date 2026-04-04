package ao.com.angotech.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // TTL default moderado para evitar dados muito desatualizados.
        // Para dados de produto, preferimos consistência maior que retenção longa.
        RedisCacheConfiguration baseConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()))
                .entryTtl(Duration.ofMinutes(3));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Produto individual: pode ficar um pouco mais tempo em cache.
        cacheConfigurations.put("product", baseConfiguration.entryTtl(Duration.ofMinutes(3)));

        // Listas (todos/categoria): dados mudam com mais frequência e podem ficar grandes.
        // TTL menor reduz risco de staleness e consumo excessivo de memória.
        cacheConfigurations.put("products", baseConfiguration.entryTtl(Duration.ofMinutes(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfiguration)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

}
