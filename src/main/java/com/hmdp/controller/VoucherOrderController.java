package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.LimitType;
import com.hmdp.utils.RateLimit;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private IShopService shopService;

    /**
     * 秒杀券抢购
     * @param voucherId
     * @return
     */
//    @RateLimit(type = LimitType.TOKEN_BUCKET, // 令牌桶限流
//            rate = 100, // 令牌生成速率：每秒生成100个令牌
//            capacity = 200, // 桶容量：200个令牌
//            permits = 1, // 每次请求消耗1个令牌
//            byUser = false // 全局限流
//    )
    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * AI实现：查询当前登录用户的优惠券订单
     */
    @GetMapping("/of/me")
    public Result queryMyOrders(
            @RequestParam(value = "current", defaultValue = "1") Integer current,// 当前页码
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize // 每页大小
    ) {
        Long userId = UserHolder.getUser().getId();
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null ? 9 : Math.min(Math.max(pageSize, 1), 50);
        Page<VoucherOrder> orderPage = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .orderByDesc(VoucherOrder::getCreateTime)
                .page(new Page<>(safeCurrent, safePageSize));
        List<VoucherOrder> orders = orderPage.getRecords();

        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (VoucherOrder order : orders) {
            Voucher voucher = voucherService.getById(order.getVoucherId());
            Shop shop = voucher == null ? null : shopService.getById(voucher.getShopId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", order.getId());
            item.put("voucherId", order.getVoucherId());
            item.put("status", order.getStatus());
            item.put("payType", order.getPayType());
            item.put("createTime", order.getCreateTime());
            item.put("payTime", order.getPayTime());
            item.put("voucherTitle", voucher == null ? "优惠券" : voucher.getTitle());
            item.put("shopId", voucher == null ? null : voucher.getShopId());
            item.put("shopName", shop == null ? "未知商家" : shop.getName());
            item.put("shopImage", shop == null ? null : shop.getImages());
            result.add(item);
        }
        Map<String, Object> pageResult = new LinkedHashMap<>();
        pageResult.put("records", result);
        pageResult.put("current", orderPage.getCurrent());
        pageResult.put("pageSize", orderPage.getSize());
        pageResult.put("total", orderPage.getTotal());
        pageResult.put("pages", orderPage.getPages());
        return Result.ok(pageResult);
    }
}
