package com.example.featureflags.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Subscribes every instance to the change channel. When any instance changes a flag,
 * all instances drop their in-memory copy and read the fresh one on the next evaluation.
 */
@Configuration
@ConditionalOnProperty(name = "featureflags.redis.enabled", havingValue = "true")
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer flagChangeListener(RedisConnectionFactory connectionFactory,
                                                            FlagCache flagCache) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // If Redis goes away, keep retrying the subscription every 5 seconds.
        container.setRecoveryInterval(5_000);
        container.addMessageListener(
                (message, pattern) -> flagCache.evictLocal(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(RedisSharedFlagCache.CHANGES_CHANNEL));
        return container;
    }
}
