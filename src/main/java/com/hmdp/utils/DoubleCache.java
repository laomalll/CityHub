package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class DoubleCache {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Cache<String, Object> shopCache;

    private static final Object NULL_VALUE = new Object(); // 缓存穿透空值的标记


    // 需要查缓存时，先调用这个方法：
    public <R> R lookup(String key,Class<R> type) {
        String caffeineKey = "caffeine:" + key; // 本地key
        String redisKey = key; // redis的key

        // 1. 先查一级缓存，即本地Caffeine本地缓存
        Object value = shopCache.getIfPresent(caffeineKey);
        if (value != null) {
            log.debug("走了Caffeine缓存！");
            return (R) value; // 返回数据
        }

        // 2. 一级缓存没中，查二级缓存，即Redis
        String json = stringRedisTemplate.opsForValue().get(redisKey); // 若无缓存，则返回的是null
        if (StrUtil.isNotBlank(json)){ // 存在，这里判断的是 shop!=null && shop.size > 0
            log.debug("走了Redis缓存！");
            // 反序列化：JSON字符串 --> Java对象
            R r = JSONUtil.toBean(json, type);
            // 回写到一级缓存，方便下次直接调用
            shopCache.put(caffeineKey,r);
            // 返回数据
            return r;
        }

        // 3. 判断命中的是否为""，如果是则说明是缓存穿透命中
        if ("".equals(json)){
            log.debug("为Redis缓存穿透空值");
            return (R) NULL_VALUE; // 返回缓存穿透空值的标记
        }

        // 4. 完全无缓存
        return null;
    }

    // 双写缓存与“防雪崩”噪点
    public <R> void put(String key, R value, long time, TimeUnit unit,Class<R> type) {
        String caffeineKey = "caffeine:" + key; // 本地key
        String redisKey = key; // redis的key
        // 写入 Caffeine缓存
        shopCache.put(caffeineKey, value);
        // 写入 Redis缓存，防止缓存雪崩策略：给过期时间加 0~120 秒的随机偏移，避免大量 key 同时过期
        long randomExpiry = time + (long) (Math.random() * 2);
        stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(value), randomExpiry, unit);
    }

    // 双删缓存,并发布订阅消息
    public void evict(String key) {
        String caffeineKey = "caffeine:" + key;
        String redisKey = key;

        // 1、清除当前机器的Caffeine缓存
        shopCache.invalidate(caffeineKey);

        // 2、清除共享的Redis缓存
        stringRedisTemplate.delete(redisKey);

        // 3、发布一条订阅消息，通知其他节点清除Caffeine缓存
        // 频道名固定为：caffeine:clear:channel，并传入需要删除的Key
        stringRedisTemplate.convertAndSend("caffeine:clear:channel", key);
    }

    /**
     * 根据指定的`Key`查询缓存，并反序列化为指定类型，需要利用`互斥锁`解决缓存击穿问题
     * @param keyPrefix 查询数据的Key前缀
     * @param lockKeyPrefix 锁的Key前缀
     * @param id 查数据库时使用
     * @param type 指定返回类型的class对象，User.class、Goods.class、Shop.class
     * @param Function<ID, R> dbFallback  当缓存不存在时，从数据库获取数据的函数（有参有返回值的函数），ID为参数类型，R为返回值类型
     * @param time Redis过期时间
     * @param unit 时间单位
     * @return
     * @param <R> 指定返回类型，可能是User、Goods、Shop，查到数据时不用强制类型转换
     * @param <ID> id类型
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, String lockKeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.先查二级缓存
        R cacheResult = lookup(key, type);
        // 判断是否为缓存穿透空值
        if (cacheResult == NULL_VALUE) {
            log.debug("为缓存穿透空值，返回null");
            return null;
        }
        if (cacheResult != null) {
            return cacheResult; // 缓存命中，返回数据
        }

        log.debug("没有缓存，查询数据库进行重建");
        // 2.本地缓存和Caffeine都未命中，则根据id查询数据库，完成缓存重建
        // 2.1 获取互斥锁
        String lockKey = lockKeyPrefix + id;
        Boolean getLock = tryLock(lockKey); // 尝试获取互斥锁，成功则返回true
        try {
            if (!getLock){ // 获取锁失败
                Thread.sleep(50); // 休眠一段时间
                return queryWithMutex(keyPrefix, lockKeyPrefix,id, type, dbFallback, time, unit); // 递归重试
            }

            // 2.2 获取锁成功，作一下 Double Check，再次检查缓存，避免重复重建缓存
            cacheResult = lookup(key, type);
            // 判断是否为缓存穿透空值
            if (cacheResult == NULL_VALUE) {
                log.debug("为缓存穿透空值，返回null");
                return null;
            }
            if (cacheResult != null) {
                return cacheResult; // 缓存命中，返回数据
            }

            // 2.3 获取锁成功，查询数据库，开始重建缓存数据
            cacheResult = dbFallback.apply(id);

            // 2.4 判断数据是否存在，如果不存在，则说明是缓存穿透，则将""写入Redis中，设置较短的过期时间，返回null
            if (cacheResult == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES); // 缓存到Redis中，设置有效期为2分钟
                return null;
            }
            // 2.5 数据存在，双写：写入Caffeine和Redis缓存
            put(key, cacheResult, time,unit,type);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 2.6 重建缓存结束，释放锁
            unlock(lockKey);
        }

        // 3. 返回响应数据
        return cacheResult;
    }

    // 获取互斥锁，成功返回true，失败返回false
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS); // 尝试获取互斥锁，这里给锁设置了有效期，防止服务异常一直不释放锁，导致死锁
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
