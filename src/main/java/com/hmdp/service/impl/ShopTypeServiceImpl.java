package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商铺类型
     * @return
     */
    @Override
    public Result queryTypeList() {
        // 1. 查询缓存
        String json = stringRedisTemplate.opsForValue().get("cache:shop:type");
        if (StrUtil.isNotBlank(json)) { // 缓存中有数据则直接返回
            List<ShopType> typeList = JSONUtil.toList(json, ShopType.class); // 反序列化：JSON字符串 ---> List<ShopType>
            return Result.ok(typeList);
        }

        // 2. 查询数据库
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        json = JSONUtil.toJsonStr(typeList); // 序列化：List<ShopType> ---> JSON字符串
        stringRedisTemplate.opsForValue().set("cache:shop:type", json); // 存储到Redis

        // 3. 返回响应数据
        return Result.ok(typeList);
    }
}
