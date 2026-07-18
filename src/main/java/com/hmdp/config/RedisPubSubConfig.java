package com.hmdp.config;
import com.hmdp.listener.CaffeineEvictListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisPubSubConfig {

    /**
     * 配置Redis消息监听器容器
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 让监听器订阅指定频道 "caffeine.clear.channel"
        container.addMessageListener(
                listenerAdapter,  // 交给谁处理
                new ChannelTopic("caffeine:clear:channel") // 监听哪个频道
        );

        // 模式匹配订阅
        // container.addMessageListener(orderNoticeListenerAdapter, new PatternTopic("*.notice"));

        return container;
    }

    /**
     * 消息监听器适配器
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(CaffeineEvictListener caffeineEvictListener) {
        // 指定处理消息的方法名
        return new MessageListenerAdapter(caffeineEvictListener, "evictShopCaffeine");
    }
}