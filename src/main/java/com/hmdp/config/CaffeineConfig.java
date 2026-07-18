package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存Caffeine缓存配置类
 */

@Configuration
public class CaffeineConfig {
    /**
     * 缓存商户信息
     */
    @Bean
    public Cache<String, Object> shopCache(){
        return Caffeine.newBuilder()
                //写入或者更新120s后，缓存过期并失效, 实际项目中肯定不会那么短时间就过期，根据具体情况设置即可
                .expireAfterWrite(120, TimeUnit.SECONDS)
                .initialCapacity(50) // 初始缓存大小
                .maximumSize(500)  // 缓存的最大条数
                .recordStats() // 开启监控统计功能
                .build();
    }
    // 获取缓存的统计信息(可选)
    public void printStats(Cache<String, Object> shopCache){
        CacheStats stats = shopCache.stats();
        System.out.println("缓存命中率：" + stats.hitRate());
        System.out.println("缓存丢失率：" + stats.missRate());
        System.out.println("缓存总访问次数：" + stats.requestCount());
        System.out.println("缓存总命中次数：" + stats.hitCount());
        System.out.println("缓存总丢失次数：" + stats.missCount());
    }
}