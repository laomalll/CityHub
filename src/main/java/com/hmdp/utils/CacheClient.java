package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 方法1：将任意`Java`对象序列化为`json`并存储在`string`类型的`Key`中，并且可以设置`TTL`过期时间
     * @param key
     * @param value
     * @param time 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time,TimeUnit unit) {
        // 序列化：Java对象 -> JSON字符串
        String json = JSONUtil.toJsonStr(value);
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key,json, time, unit);
    }

    /**
     * 方法2：将任意`Java`对象序列化为`json`并存储在`string`类型的`Key`中，并且可以设置`逻辑过期时间`，用于处理`缓存击穿`问题
     * @param key
     * @param value
     * @param time 逻辑过期时间
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time,TimeUnit unit) {
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 序列化：Java对象 -> JSON字符串
        String json = JSONUtil.toJsonStr(redisData);

        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, json);
    }


    /**
     * 方法3：根据指定的`Key`查询缓存，并反序列化为指定类型，利用`缓存空数据`的方式解决缓存穿透问题
     * @param keyPrefix 查询数据的Key前缀
     * @param id 查数据库时使用
     * @param type 指定返回类型的class对象，User.class、Goods.class、Shop.class
     * @param Function<ID, R> dbFallback  当缓存不存在时，从数据库获取数据的函数（有参有返回值的函数），ID为参数类型，R为返回值类型
     * @param time 过期时间
     * @param unit 时间单位
     * @return
     * @param <R> 指定返回类型，可能是User、Goods、Shop，查到数据时不用强制类型转换
     * @param <ID> id类型
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){

        // 1、从Redis中查询缓存是否存在
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) { // 这里判断的是 shop!=null && shop.size > 0
            return JSONUtil.toBean(json, type); // 反序列化：JSON字符串 --> Java对象
        }

        // 2、判断命中的是否为"",如果是，则说明是缓存穿透命中,返回null即可
        if ("".equals(json)){
            return null;
        }

        // 3、缓存未命中，根据id查询数据库
        R r = dbFallback.apply(id);

        // 4、判断数据是否存在，如果是，则说明是缓存穿透，则将""写入Redis中，设置较短的过期时间，返回null
        if (r == null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES); // 设置有效期为2分钟
            return null;
        }

        // 5、存在，则写入Redis缓存
        json = JSONUtil.toJsonStr(r); // 序列化：Java对象 -> JSON字符串
        stringRedisTemplate.opsForValue().set(key, json, time, unit);

        // 6、返回响应数据
        return r;

    }

    /**
     * 方法4：根据指定的`Key`查询缓存，并反序列化为指定类型，需要利用`逻辑过期`解决缓存击穿问题
     * @param keyPrefix 查询数据的Key前缀
     * @param localKeyPrefix 互斥锁的Key前缀
     * @param id 查数据库时使用
     * @param type 指定返回类型的class对象，User.class、Goods.class、Shop.class
     * @param Function<ID, R> dbFallback  当缓存不存在时，从数据库获取数据的函数（有参有返回值的函数），ID为参数类型，R为返回值类型
     * @param time 过期时间
     * @param unit 时间单位
     * @return
     * @param <R> 指定返回类型，可能是User、Goods、Shop，查到数据时不用强制类型转换
     * @param <ID> id类型
     */
    // 创建10条线程的线程池，最多10个线程同时跑"重建缓存"
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, String localKeyPrefix,ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        // 1.从redis中查询商铺缓存是否存在
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key); // 若无缓存，则返回的是null
        if (StrUtil.isBlank(json)) { // 存在则直接返回，这里判断的是 shop !=null && shop.size > 0
            return null;
        }

        // 2. 缓存命中，需要先把json反序列为Java对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class); // 反序列化：JSON字符串 --> RedisData
        JSONObject data = (JSONObject)redisData.getData(); // 这里不能直接把data强转为Shop，只能先转为JSONObject类型
        R r = JSONUtil.toBean(data, type); // 转为R类型

        // 3. 判断缓存是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())){ // 未过期，直接返回缓存数据
            return r;
        }

        // 4.缓存数据过期，获取互斥锁,准备重建缓存数据
        // 4.1 获取互斥锁
        String lockKey = localKeyPrefix + id;
        Boolean getLock = tryLock(lockKey); // 尝试获取互斥锁，成功则返回true
        if (!getLock){ // 获取互斥锁失败，直接返回过期数据
            return r;
        }

        // 4.2 这里还可以做个Double Check，再次检查缓存，避免多线程并发下，重复重建缓存

        // 4.3 开启异步线程重建缓存数据，主线程不会被阻塞
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            // 从数据库中查询数据
            R newR = dbFallback.apply(id);

            // 封装逻辑过期时间
            RedisData newRedisData = new RedisData();
            newRedisData.setData(newR);
            newRedisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

            // 序列化：Java对象 --> JSON字符串
            String newJson = JSONUtil.toJsonStr(newRedisData);

            // 写入Redis
            stringRedisTemplate.opsForValue().set(key, newJson);

            // 释放锁
            unlock(lockKey);

        });

        // 5. 返回过期数据
        return r;
    }

    /**
     * 方法5：根据指定的`Key`查询缓存，并反序列化为指定类型，需要利用`互斥锁`解决缓存击穿问题
     * @param keyPrefix 查询数据的Key前缀
     * @param lockKeyPrefix 锁的Key前缀
     * @param id 查数据库时使用
     * @param type 指定返回类型的class对象，User.class、Goods.class、Shop.class
     * @param Function<ID, R> dbFallback  当缓存不存在时，从数据库获取数据的函数（有参有返回值的函数），ID为参数类型，R为返回值类型
     * @param time 过期时间
     * @param unit 时间单位
     * @return
     * @param <R> 指定返回类型，可能是User、Goods、Shop，查到数据时不用强制类型转换
     * @param <ID> id类型
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, String lockKeyPrefix,ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1.从redis中查询商铺缓存是否存在
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key); // 若无缓存，则返回的是null
        if (StrUtil.isNotBlank(json)) { // 存在则直接返回，这里判断的是 shop!=null && shop.size > 0
            return JSONUtil.toBean(json, type); // 反序列化：JSON字符串 --> Java对象
        }

        // 2.判断命中的是否为""，如果是则说明是缓存穿透命中,返回null即可
        if ("".equals(json)){
            return null;
        }

        // 3.缓存未命中，则根据id查询数据库，完成缓存重建
        // 3.1 获取互斥锁
        String lockKey = lockKeyPrefix + id;
        Boolean getLock = tryLock(lockKey); // 尝试获取互斥锁，成功则返回true
        R r = null;
        try {
            if (!getLock){ // 获取锁失败
                Thread.sleep(50); // 休眠一段时间
                return queryWithMutex(keyPrefix, lockKeyPrefix,id, type, dbFallback, time, unit); // 递归重试
            }

            // 3.2 获取锁成功，作一下 Double Check，再次检查缓存，避免重复重建缓存
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) { // 存在则直接返回，这里判断的是 shop!=null && shop.size > 0
                return JSONUtil.toBean(json, type); // 反序列化：JSON字符串 --> Java对象
            }
            if ("".equals(json)){
                return null;
            }

            // 3.3 获取锁成功，查询数据库，开始重建缓存数据
            r = dbFallback.apply(id);

            // 3.4 判断数据是否存在，如果是，则说明是缓存穿透，则将""写入Redis中，设置较短的过期时间，返回null
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES); // 缓存到Redis中，设置有效期为2分钟
                return null;
            }
            // 3.5 数据存在，写入redis缓存
            json = JSONUtil.toJsonStr(r); // 序列化：Java对象 --> JSON字符串
            stringRedisTemplate.opsForValue().set(key, json, time, unit); // 存储到Redis
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 3.6 重建缓存结束，释放锁
            unlock(lockKey);
        }

        // 4. 返回响应数据
        return r;
    }

    // 获取锁，成功返回true，失败返回false
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS); // 尝试获取互斥锁，这里给锁设置了有效期，防止服务异常一直不释放锁，导致死锁
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
