package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(shopId);

        // 返回结果
        return Result.ok(vouchers);
    }

    /**
     * 添加秒杀券
     * @param voucher
     */
    @Override
    @Transactional // 涉及到两张表的添加操作，开启事务
    public void addSeckillVoucher(Voucher voucher) {
        // 保存秒杀券的基本信息到tb_voucher表
        voucherMapper.insert(voucher);
        // 保存秒杀信息到tb_seckill_voucher表
        SeckillVoucher seckillVoucher = SeckillVoucher.builder()
                .voucherId(voucher.getId()) //秒杀券id
                .stock(voucher.getStock()) //库存
                .beginTime(voucher.getBeginTime()) //抢购开始时间
                .endTime(voucher.getEndTime()) //抢购结束时间
                .build();
        seckillVoucherService.save(seckillVoucher);

        // 保存该秒杀券的库存 + 开始时间 + 抢购结束时间到到Redis中,使用Hash来存储
        // 2. 封装Hash字段数据，需要全部转换为字符串
        Map<String, String> map = new HashMap<>();
        map.put("stock", voucher.getStock().toString()); // 库存数量
        // 将时间转换为时间戳(秒)
        long beginTime = voucher.getBeginTime().toEpochSecond(ZoneOffset.UTC); // 开始抢购时间的时间戳
        long endTime = voucher.getEndTime().toEpochSecond(ZoneOffset.UTC); // 结束抢购时间的时间戳
        map.put("beginTime", Long.toString(beginTime));
        map.put("endTime", Long.toString(endTime));

        // 3. 存储到Redis中，key："seckill:info:" + 秒杀券id， value：Hash字段数据
        stringRedisTemplate.opsForHash().putAll("seckill:info:" + voucher.getId(), map);
    }
}
