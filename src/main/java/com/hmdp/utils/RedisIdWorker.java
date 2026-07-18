package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 使用Redis生成全局唯一ID
 */

@Component
public class RedisIdWorker {
    /**
     * 自定义一个开始时间（以秒为单位）
     */
    private static final long BEGIN_TIMESTAMP = 1777998730L;
    /**
     * 序列号的位数（支持每秒内生成2^32个不同的ID）
     */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // keyPrefix：相关业务前缀
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now(); // 获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC); // 转换为秒
        long timestamp = nowSecond - BEGIN_TIMESTAMP; // 时间戳 = 当前时间 - 开始时间

        // 2.生成序列号
        // 2.1.获取当前日期（具体到天），按日期拆分Key，每天生成一个新的Key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }
}