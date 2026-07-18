package com.hmdp.utils;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;


// 自定义限流注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流类型
     */
    LimitType type() default LimitType.FIXED_WINDOW;

    /**
     * 限流 key。
     * 不写时默认使用请求 URI（请求路径）。
     */
    String key() default "";

    /**
     * 固定窗口限流：窗口内最多允许多少次请求
     */
    long limit() default 100;

    /**
     * 固定窗口限流：窗口时间：60s，默认60秒内最多允许 limit 个请求
     */
    long window() default 60;

    /**
     * 时间单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 令牌桶：令牌生成速率，标识每秒生成多少个令牌
     */
    long rate() default 100;

    /**
     * 令牌桶：桶容量
     */
    long capacity() default 200;

    /**
     * 每次请求消耗几个令牌，默认为1
     */
    long permits() default 1;

    /**
     * 是否按照用户维度限流。
     * true：每个用户单独限流，例如/shop/{id}接口，同一用户在60s内只能请求100次
     * false：接口全局限流，例如/shop/{id}接口，所有用户在60s内只能请求100次
     */
    boolean byUser() default true;
}