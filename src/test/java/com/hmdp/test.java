package com.hmdp;


import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class test {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedissonClient redissonClient_6380;

    @Resource
    private RedissonClient redissonClient_6381;

    // 联锁
    private RLock lock;

    @BeforeEach
    void setUp(){
        // 创建锁对象
        RLock lock1 = redissonClient.getLock("lock:order:100");
        RLock lock2 = redissonClient_6380.getLock("lock:order:100");
        RLock lock3 = redissonClient_6381.getLock("lock:order:100");

        // 创建联锁 redissonClient、redissonClient_6380、redissonClient_6381调用getMultiLock效果都是一样的，任选其一即可
        lock = redissonClient.getMultiLock(lock1, lock2, lock3); // 传入多个锁
    }


    @Test
    void method1() throws Exception {
        // 尝试获取锁
        boolean isLock = lock.tryLock(1, 10,TimeUnit.MINUTES);

        // 获取锁失败
        if (!isLock) {
            System.out.println("获取锁失败，method1");
            return;
        }
        // 获取锁成功
        try{
            System.out.println("获取锁成功，method1");
            method2();
        }finally {
            System.out.println("释放锁，method1");
            lock.unlock();
        }
    }

    void method2() throws Exception {
        // 尝试获取锁
        boolean isLock = lock.tryLock(1, 10, TimeUnit.MINUTES);

        // 获取锁失败
        if (!isLock) {
            System.out.println("获取锁失败，method2");
            return;
        }
        // 获取锁成功
        try{
            System.out.println("获取锁成功，method2");
        }finally {
            System.out.println("释放锁，method2");
            lock.unlock();
        }
    }

}
