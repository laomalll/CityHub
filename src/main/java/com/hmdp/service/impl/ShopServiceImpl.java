package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.Location;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private DoubleCache doubleCache;

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    public Result queryById(Long id) {
//        // 1、解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(
//                CACHE_SHOP_KEY, // 查询数据的Key前缀
//                id, // 商户id
//                Shop.class, // 返回类型的class对象
//                id2 -> shopMapper.selectById(id2), // id2代表参数
//                CACHE_SHOP_TTL, // 数据缓存有效时间
//                TimeUnit.MINUTES // 时间单位
//        );

//        // 2、缓存空值解决缓存穿透、随机天机TTL解决缓存雪崩、互斥锁解决缓存击穿
//        Shop shop = cacheClient.queryWithMutex(
//                CACHE_SHOP_KEY,// 查询数据的Key前缀
//                LOCK_SHOP_KEY, // 互斥锁的Key前缀
//                id, // 商户id
//                Shop.class, // 返回类型的class对象
//                id2 -> shopMapper.selectById(id2), // id2代表参数
//                CACHE_SHOP_TTL, // 数据缓存有效时间
//                TimeUnit.MINUTES // 时间单位
//        );


        // 3、逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(
//                CACHE_SHOP_KEY,// 查询数据的Key前缀
//                LOCK_SHOP_KEY, // 互斥锁的Key前缀
//                id, // 商户id
//                Shop.class, // 返回类型的class对象
//                id2 -> shopMapper.selectById(id2), // id2代表参数
//                CACHE_SHOP_TTL, // 数据缓存有效时间
//                TimeUnit.MINUTES // 时间单位
//        );

        // 4、二级缓存：缓存空值解决缓存穿透、随机天机TTL解决缓存雪崩、互斥锁解决缓存击穿
        Shop shop = doubleCache.queryWithMutex(
                CACHE_SHOP_KEY,// 查询数据的Key前缀
                LOCK_SHOP_KEY, // 互斥锁的Key前缀
                id, // 商户id
                Shop.class, // 返回类型的class对象
                id2 -> shopMapper.selectById(id2), // id2代表参数
                CACHE_SHOP_TTL, // 数据缓存有效时间
                TimeUnit.MINUTES // 时间单位
        );

        // 4、返回响应数据
        if (shop == null){
            return Result.fail("店铺不存在！！！！");
        }
        return Result.ok(shop);
    }



    /**
     * 根据id查询商铺信息（缓存空数据解决缓存穿透问题）
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryWithPassThrough(Long id){
        // 1.从redis中查询商铺缓存是否存在
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key); // 若无缓存，则返回的是null
        if (StrUtil.isNotBlank(shopJson)) { // 存在则直接返回，这里判断的是 shop!=null && shop.size > 0
            Shop shop = JSONUtil.toBean(shopJson, Shop.class); // 反序列化：JSON字符串 --> Shop
            return Result.ok(shop);
        }

        // 2.判断命中的是否为""，如果是则说明是缓存穿透命中
        if ("".equals(shopJson)){
            return Result.fail("店铺不存在！！！");
        }

        // 3.缓存中没有，则根据id查询数据库
        Shop shop = shopMapper.selectById(id);

        // 3. 判断该店铺是否存在，如果不存在，说明是缓存穿透
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES); // 存储到Redis中，设置有效期为2分钟
            return Result.fail("店铺不存在！！！");
        }

        // 4.存在，则写入redis缓存
        String json = JSONUtil.toJsonStr(shop); // 序列化：Shop -> JSON字符串
        stringRedisTemplate.opsForValue().set(key, json, CACHE_SHOP_TTL, TimeUnit.MINUTES); // 存储到Redis，并设置超时时间

        // 5. 返回响应数据
        return Result.ok(shop);
    }

    /**
     * 根据id查询商铺信息（互斥锁解决缓存击穿问题）
     * @param id
     * @return
     */
    @Override
    public Result queryWithMutex(Long id){
        // 1.从redis中查询商铺缓存是否存在
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key); // 若无缓存，则返回的是null
        if (StrUtil.isNotBlank(shopJson)) { // 存在则直接返回，这里判断的是 shop!=null && shop.size > 0
            Shop shop = JSONUtil.toBean(shopJson, Shop.class); // 反序列化：JSON字符串 --> Shop
            return Result.ok(shop);
        }

        // 2.判断命中的是否为""，如果是则说明是缓存穿透命中
        if ("".equals(shopJson)){
            return Result.fail("店铺不存在！！！");
        }

        // 3.缓存中没有，则根据id查询数据库，完成缓存重建
        // 3.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean getLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS); // 尝试获取互斥锁，成功则返回true
        Shop shop = null;
        try {
            if (!getLock){ // 获取锁失败
                Thread.sleep(50); // 休眠
                return queryWithMutex(id); // 递归重试
            }

            // 3.2 Double Check，再次检查缓存，避免重复重建缓存
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) { // 存在则直接返回，这里判断的是 shop!=null && shop.size > 0
                shop = JSONUtil.toBean(shopJson, Shop.class); // 反序列化：JSON字符串 --> Shop
                return Result.ok(shop);
            }
            if ("".equals(shopJson)){
                return Result.fail("店铺不存在！！！");
            }

            // 3.3 获取锁成功，查询数据库，开始重建缓存数据
            shop = shopMapper.selectById(id);
            //Thread.sleep(100); // 休眠，模拟重建缓存的耗时操作

            // 3.4 店铺不存在，说明是缓存穿透，缓存空对象
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES); // 缓存空对象到Redis中，设置有效期为2分钟
                return Result.fail("店铺不存在！！！");
            }
            // 3.5 店铺存在，写入redis缓存
            String json = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_SHOP_TTL, TimeUnit.MINUTES); // 存储到Redis，设置有效期为30分钟
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 3.6 重建缓存结束，释放锁
            stringRedisTemplate.delete(lockKey);
        }

        // 4. 返回响应数据
        return Result.ok(shop);
    }


    // 创建10条线程的线程池，最多10个线程同时跑"重建缓存"
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询商铺信息（逻辑过期解决缓存击穿问题）
     * @param id
     * @return
     */
    @Override
    public Result queryWithLogicalExpire(Long id){
        // 1.从redis中查询商铺缓存是否存在
        String key  = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key); // 若无缓存，则返回的是null
        if (StrUtil.isBlank(shopJson)) { // 存在则直接返回，这里判断的是 shop !=null && shop.size > 0
            return Result.fail("店铺不存在");
        }

        // 2. 缓存命中，需要先把json反序列为Java对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class); // 反序列化：JSON字符串 --> RedisData
        JSONObject data = (JSONObject)redisData.getData(); // 这里不能直接把data强转为Shop，只能先转为JSONObject类型
        Shop shop = JSONUtil.toBean(data, Shop.class); // 转为Shop类型

        // 3. 判断缓存是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())){ // 未过期，直接返回缓存数据
            return Result.ok(shop);
        }

        // 4.缓存数据过期，获取互斥锁,准备重建缓存数据
        // 4.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean getLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS); // 尝试获取互斥锁，成功则返回true
        if (!getLock){ // 获取互斥锁失败，直接返回过期数据
            return Result.ok(shop);
        }

        // 4.2 这里还可以做个Double Check，再次检查缓存，避免多线程并发下，重复重建缓存

        // 4.3 开启异步线程重建缓存数据，主线程不会被阻塞
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            // 查询店铺数据
            Shop newShop = shopMapper.selectById(id);

            // 封装逻辑过期时间
            RedisData newRedisData = new RedisData();
            newRedisData.setData(newShop);
            newRedisData.setExpireTime(LocalDateTime.now().plusSeconds(CACHE_SHOP_TTL));

            // 序列化：Java对象 --> JSON字符串
            String json = JSONUtil.toJsonStr(newRedisData);

            // 写入Redis
            stringRedisTemplate.opsForValue().set(key, json);

            // 释放锁
            stringRedisTemplate.delete(lockKey);

        });

        // 5. 返回过期数据
        return Result.ok(shop);
    }

    /**
     * 修改商铺信息
     * @param shop
     * @return
     */
    @Override
    @Transactional // 添加事务,保证更新数据库的数据一致性
    public Result updateShop(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        shopMapper.updateById(shop);

        // 2.缓存双删
        stringRedisTemplate.setEnableTransactionSupport(true); // @Transactional只对数据库有效，我们这里需要给Redis手动开启事务
        doubleCache.evict(CACHE_SHOP_KEY + shop.getId());

        // 3.返回相应数据
        return Result.ok();
    }



    /**
     * 缓存预热
     * @param id 店铺id
     * @param expireSeconds 有效时间（单位为秒数）
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1. 查询店铺数据
        Shop shop = shopMapper.selectById(id);

        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds)); // 逻辑过期时间

        // 3. 序列化：Java对象 -> JSON字符串
        String json = JSONUtil.toJsonStr(redisData);

        // 4. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, json);
    }

    /**
     * 根据商铺类型分页查询商铺信息（需要根据地理位置，优先推荐地理位置近的）
     * @param typeId 商铺类型
     * @param current 页码
     * @param x 经度
     * @param y 纬度
     * @return 商铺列表
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current,Double x,Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库分页查询即可
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.根据坐标查询，从Redis中，获取离当前位置最近的商铺
        String key = "shop:geo:" + typeId;
        // 2.1 设置分页参数（在Redis中分页）
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE; // 当前页的起始索引
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE; // 当前页的结束索引
        // 2.2 查询
        // GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m Count end ASC WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y), // 中心坐标
                new Distance(5000), // 半径,单位默认为m
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance() // WITHDISTANCE
                        .limit(end) // COUNT end
                        .sortAscending()); // ASC

        // 3.解析结果
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        // 4. 获取存储内容
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (from > list.size()){ // 判断当前页是否有对应数据
            // 没有下一页数据了 解释：只有10条数据，假设我们要查询第3页数据，此时 from = 20，而list.size() = 10，则from = 20 > list.size() = 10，说明没有下一页数据了
            return Result.ok(Collections.emptyList());
        }

        // 5、截取出from ~ end的商铺
        List<Long> ids = new ArrayList<>();
        Map<String,Distance> distanceMap = new HashMap<>(); // key：店铺id value：距离
        list.stream().skip(from).forEach(geoResult -> {
            // 5.1 获取店铺id
            String shopIdStr = geoResult.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));

            // 5.2 获取店铺距离
            Distance distance = geoResult.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        if (ids.size() == 0){
            return Result.fail("当前分类无商铺信息");

        }

        // 6、查询这些店铺
        LambdaQueryWrapper<Shop> shopWrapper = new LambdaQueryWrapper<>();
        shopWrapper.in(Shop::getId, ids);
        List<Shop> shops = shopMapper.selectList(shopWrapper); // 这里Mysql的查询数据不会按照ids的顺序返回

        // 7、查询结果按照ids集合的顺序进行排序
        shops.sort(new Comparator<Shop>() {
            @Override
            public int compare(Shop o1, Shop o2) {
                return ids.indexOf(o1.getId()) - ids.indexOf(o2.getId());
            }
        });

        // 8、为每个店铺添加距离属性distance
        shops.forEach(shop ->{
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        });

        // 9、返回结果
        return Result.ok(shops);
    }
}
