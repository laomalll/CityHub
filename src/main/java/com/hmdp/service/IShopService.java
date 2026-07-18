package com.hmdp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id);


    /**
     * 根据id查询商铺信息（解决缓存击穿问题）
     * @param id 商铺id
     * @return 商铺详情数据
     */
    Result queryWithPassThrough(Long id);

    /**
     * 根据id查询商铺信息（互斥锁解决缓存击穿问题）
     * @param id
     * @return
     */
    Result queryWithMutex(Long id);

    /**
     * 根据id查询商铺信息（逻辑过期解决缓存击穿问题）
     * @param id
     * @return
     */
    Result queryWithLogicalExpire(Long id);

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    Result updateShop(Shop shop);


    /**
     * 根据商铺类型分页查询商铺信息（需要根据地理位置，优先推荐地理位置近的）
     * @param typeId 商铺类型
     * @param current 页码
     * @param x 经度
     * @param y 纬度
     * @return 商铺列表
     */
    Result queryShopByType(Integer typeId, Integer current, Double x,Double y);
}
