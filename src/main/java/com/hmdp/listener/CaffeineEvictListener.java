package com.hmdp.listener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import com.github.benmanes.caffeine.cache.Cache;

@Component
public class CaffeineEvictListener{

    @Resource
    private Cache<String, Object> shopCache;

    /**
     * 收到商铺缓存清理消息，执行清理（caffeineKey会通过Redis底层自动传递过来）
     */
    public void evictShopCaffeine(String caffeineKey) {
        System.out.println("收到商铺缓存清理消息，caffeineKey：" + caffeineKey);
        // 清除Caffeine缓存
        shopCache.invalidate(caffeineKey);
    }

}