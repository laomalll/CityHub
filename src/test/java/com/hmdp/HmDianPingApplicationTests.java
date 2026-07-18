package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;




    @Resource
    private CacheClient cacheClient;
    @Test
    void testSaveShop2Redis(){
        shopService.saveShop2Redis(1L, 10L);

        System.out.println(LocalDateTime.now());
    }


    // 获取当前时间（以秒为单位）
    @Test
    void getNow(){
        LocalDateTime now = LocalDateTime.now(); // 获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC); // 转换为秒
        System.out.println(nowSecond);
    }

    // 测试利用Redis生成ID
    @Test
    void testRedisIdWorker(){
        for (int i = 0; i < 100; i++) {
            long id = redisIdWorker.nextId("order");
            System.out.println("id = " + id);
        }
    }





}
