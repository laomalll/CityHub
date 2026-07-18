package com.hmdp;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.*;
import com.hmdp.mapper.*;
import com.hmdp.service.IBlogService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IUserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.FileWriter;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SpringBootTest
public class test2 {
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;

    @Resource
    private IUserService userService;


    @Resource
    private BlogMapper blogMapper;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Test
    void saveVoucher2Redis(){
        // 1、读取秒杀券信息
        LambdaQueryWrapper<SeckillVoucher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillVoucher::getVoucherId, 18L);
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectOne(wrapper);

        // 2、存储到Redis中
        // 保存该秒杀券的库存 + 开始时间 + 抢购结束时间到到Redis中,使用Hash来存储
        // 封装Hash字段数据，需要全部转换为字符串
        Map<String, String> map = new HashMap<>();
        map.put("stock", seckillVoucher.getStock().toString()); // 库存数量
        // 将时间转换为时间戳(秒)
        long beginTime = seckillVoucher.getBeginTime().toEpochSecond(ZoneOffset.UTC); // 开始抢购时间的时间戳
        long endTime = seckillVoucher.getEndTime().toEpochSecond(ZoneOffset.UTC); // 结束抢购时间的时间戳
        map.put("beginTime", Long.toString(beginTime));
        map.put("endTime", Long.toString(endTime));

        // 3. 存储到Redis中，key："seckill:info:" + 秒杀券id， value：Hash字段数据
        stringRedisTemplate.opsForHash().putAll("seckill:info:" + seckillVoucher.getVoucherId(), map);
    }


    @Test
    void UserLoginByPassword () throws IOException, InterruptedException {
        // 利用IO流把生成的token依次追加到txt文件中
        FileWriter fw = new FileWriter("E:\\tokens.txt", true);

        // 查询表中前500个用户信息
        Page<User> page = new Page<>(1,1000);
        userMapper.selectPage(page, null);
        List<User> userList = page.getRecords();
        System.out.println(userList.size());

        for (User user : userList){
            // 循环登录
            LoginFormDTO loginFormDTO = LoginFormDTO.builder()
                    .password(null)
                    .phone(user.getPhone())
                    .build();

            // 获取token
            Result result = userService.loginByPassword(loginFormDTO, null);
            String token = (String)result.getData();

            // 写入到文件中
            fw.write(token + "\n");

        }

        // 关闭流
        fw.close();
    }

    // 把每篇博文的点赞信息提前缓存到Redis中
    @Test
    void save2Redis(){
//        // 1、读取所有博文
//        List<Blog> blogList = blogMapper.selectList(null);
//
//        for (Blog blog : blogList){
//            blog.get
//        }
//
//
//
//        LambdaQueryWrapper<SeckillVoucher> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(SeckillVoucher::getVoucherId, 18L);
//        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectOne(wrapper);
//
//        // 2、存储到Redis中
//        // 保存该秒杀券的库存 + 开始时间 + 抢购结束时间到到Redis中,使用Hash来存储
//        // 封装Hash字段数据，需要全部转换为字符串
//        Map<String, String> map = new HashMap<>();
//        map.put("stock", seckillVoucher.getStock().toString()); // 库存数量
//        // 将时间转换为时间戳(秒)
//        long beginTime = seckillVoucher.getBeginTime().toEpochSecond(ZoneOffset.UTC); // 开始抢购时间的时间戳
//        long endTime = seckillVoucher.getEndTime().toEpochSecond(ZoneOffset.UTC); // 结束抢购时间的时间戳
//        map.put("beginTime", Long.toString(beginTime));
//        map.put("endTime", Long.toString(endTime));
//
//        // 3. 存储到Redis中，key："seckill:info:" + 秒杀券id， value：Hash字段数据
//        stringRedisTemplate.opsForHash().putAll("seckill:info:" + seckillVoucher.getVoucherId(), map);
    }


    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> shopList = shopMapper.selectList(null); // 查询所有店铺信息
        // 2、写入Redis中
        for (Shop shop : shopList) {
            // 2.1.获取店铺类型
            Long typeId = shop.getTypeId();
            String key = "shop:geo:" + typeId;
            // 2.2.获取店铺的经纬度
            Double x = shop.getX();
            Double y = shop.getY();
            // 2.3.写入Redis GEOADD key 经度 纬度 member
            stringRedisTemplate.opsForGeo().add(key, new Point(x, y), shop.getId().toString());
        }
    }

    // 批量地向Redis中插入HyperLogLog数据
    @Test
    void testHyperLogLog(){
        // 准备数组，装用户数据
        String[] users = new String[1000]; // 每次插入1000条数据
        // 数组索引
        int index = 0;
        for (int i = 0; i < 1000000; i++) {
            // 赋值
            users[index] = "user_" + i;
            index++;
            // 每1000条发送一次
            if (index == 1000){
                index = 0; // 重置数组索引
                // PFADD key element
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }

        // 统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("size = " + size);
    }

    // 删除Redis中以login:token开头的key
    @Test
    void deleteLoginToken(){
        // 1.获取所有以login:token开头的key
        Set<String> keys = stringRedisTemplate.keys("login:token:*");
        // 2.批量删除
        stringRedisTemplate.delete(keys);
    }






}
