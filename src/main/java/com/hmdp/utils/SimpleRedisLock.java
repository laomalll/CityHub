package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;

    // 锁的key
    private String name;

    // 锁的key前缀
    private static final String KEY_PREFIX="lock:";

    // 为每台JVM生成一个静态的、不可修改的UUID，作为锁的value的前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 释放锁的lua脚本，Long为脚本返回值
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // 初始化lua脚本
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); //设置脚本
        UNLOCK_SCRIPT.setResultType(Long.class); // 设置返回值类型为Long
    }

    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的过期时间
     * @return true代表获取锁成功，false代表获取锁失败
     */
    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 返回获取结果
        if ( success == null || success == false) return false;
        return true;
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {

        /**
         * 使用lua脚本来确保redis命令的原子性
         */

        // 锁的key
        String key = KEY_PREFIX + name;

        // 获取当前线程标识
        String currentThreadId = ID_PREFIX + Thread.currentThread().getId();

        // 封装key
        List<String> keys = new ArrayList<>();
        keys.add(key);

        // Redis 命令：eval "return redis.call('get', KEYS[1]) == ARGV[1]" 1 KEYS[1] ARGV[1]
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT, //lua脚本
                keys, // key
                currentThreadId);
    }
}
