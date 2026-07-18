package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

@Slf4j
@Aspect // 标记为切面
@Component // 注入IOC容器
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 固定窗口限流 Lua 脚本
     *
     * 适合普通接口，例如：
     * 1 分钟内最多访问 60 次
     *
     * KEYS[1]：限流 key
     * ARGV[1]：窗口时间，单位秒
     * ARGV[2]：窗口内最大访问次数
     *
     * 执行逻辑：
     * 1. 当前 key 计数 +1
     * 2. 如果是第一次访问，需要给 key 设置过期时间
     * 3. 如果当前访问次数超过限制，返回 0
     * 4. 否则返回 1
     */
    private static final String FIXED_WINDOW_SCRIPT =
            "local current = redis.call('incr', KEYS[1]) " +
            "if tonumber(current) == 1 then " +
            "   redis.call('expire', KEYS[1], ARGV[1]) " +
            "end " +
            "if tonumber(current) > tonumber(ARGV[2]) then " +
            "   return 0 " +
            "else " +
            "   return 1 " +
            "end";

    /**
     * 令牌桶限流 Lua 脚本
     *
     * 适合秒杀接口、下单接口这种高并发接口。
     *
     * KEYS[1]：令牌桶 key
     *
     * ARGV[1]：桶容量 capacity
     * ARGV[2]：令牌生成速率 rate，表示每秒生成多少令牌
     * ARGV[3]：当前时间戳 now，单位毫秒
     * ARGV[4]：当前请求需要消耗几个令牌
     * ARGV[5]：Redis key 的过期时间，单位秒
     *
     * Redis Hash 结构：
     * key = rate_limit:TOKEN_BUCKET:/voucher-order/seckill:global
     *
     * hash field:
     * tokens    当前剩余令牌数
     * timestamp 上一次刷新令牌的时间
     *
     * 执行逻辑：
     * 1. 从 Redis 取出当前桶里的 tokens 和 timestamp
     * 2. 如果桶不存在，说明第一次访问，初始化满桶
     * 3. 根据距离上次访问的时间，计算应该补充多少令牌
     * 4. 新令牌数不能超过桶容量
     * 5. 如果令牌足够，则扣减令牌并放行
     * 6. 如果令牌不足，则拒绝请求
     * 7. 把新的 tokens 和 timestamp 写回 Redis
     */
    private static final String TOKEN_BUCKET_SCRIPT =
            "local key = KEYS[1] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local rate = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "local requested = tonumber(ARGV[4]) " +
            "local ttl = tonumber(ARGV[5]) " +

            "local bucket = redis.call('hmget', key, 'tokens', 'timestamp') " +
            "local tokens = tonumber(bucket[1]) " +
            "local timestamp = tonumber(bucket[2]) " +

            "if tokens == nil then " +
            "   tokens = capacity " +
            "   timestamp = now " +
            "end " +

            "local delta = math.max(0, now - timestamp) / 1000 " +
            "local refill = delta * rate " +
            "tokens = math.min(capacity, tokens + refill) " +

            "local allowed = 0 " +
            "if tokens >= requested then " +
            "   allowed = 1 " +
            "   tokens = tokens - requested " +
            "end " +

            "redis.call('hmset', key, 'tokens', tokens, 'timestamp', now) " +
            "redis.call('expire', key, ttl) " +

            "return allowed";

    /**
     * AOP 环绕通知
     *
     * 含义：
     * 只要 Controller 或 Service 方法上加了 @RateLimit 注解，
     * 方法执行前都会先进入这个 around 方法。
     *
     * ProceedingJoinPoint joinPoint：
     * 表示被拦截的目标方法。
     * 调用 joinPoint.proceed() 才会真正执行原来的业务方法。
     *
     * RateLimit rateLimit：
     * 表示当前方法上的 @RateLimit 注解对象，
     * 可以读取注解中的限流类型、阈值、窗口大小等配置。
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {

        // 1. 根据当前的请求、用户信息、注解参数等，构造 Redis 限流 key
        String limitKey = buildLimitKey(rateLimit);

        boolean allowed;

        // 2. 根据注解上的限流类型，选择不同的限流算法
        if (rateLimit.type() == LimitType.FIXED_WINDOW) {
            // 普通接口：固定窗口限流
            allowed = fixedWindowLimit(limitKey, rateLimit);
        } else {
            // 秒杀接口：令牌桶限流
            allowed = tokenBucketLimit(limitKey, rateLimit);
        }

        // 3. 如果不允许通过，直接返回失败，不执行原来的业务方法
        if (!allowed) {
            log.warn("请求被限流，key = {}", limitKey);
            return Result.fail("请求过于频繁，请稍后再试");
        }

        // 4. 限流通过，继续执行原来的 Controller 或 Service 方法
        return joinPoint.proceed();
    }

    /**
     * 固定窗口限流
     *
     * 例如：
     * 60 秒内最多允许访问 30 次
     *
     * Redis 中大概长这样：
     *
     * key   = rate_limit:FIXED_WINDOW:/shop/1:user:1001
     * value = 15
     * ttl   = 还剩 40 秒过期
     */
    private boolean fixedWindowLimit(String key, RateLimit rateLimit) {

        // 创建 Redis Lua 脚本对象
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        // 设置Lua脚本内容
        script.setScriptText(FIXED_WINDOW_SCRIPT);

        // 设置脚本返回值类型
        script.setResultType(Long.class);

        // 把注解里的 window 转成秒
        // 例如：window = 1, unit = MINUTES，则这里变成 60 秒
        long windowSeconds = rateLimit.unit().toSeconds(rateLimit.window());

        // 执行 Lua 脚本
        Long result = stringRedisTemplate.execute(
                script,

                // KEYS[1]：限流key
                Collections.singletonList(key),

                // ARGV[1]：窗口时间
                String.valueOf(windowSeconds),

                // ARGV[2]：最大访问次数
                String.valueOf(rateLimit.limit())
        );

        // Lua 返回 1 表示允许通过，返回 0 表示被限流
        return result != null && result == 1;
    }

    /**
     * 令牌桶限流
     *
     * 例如：
     * rate = 100，capacity = 200
     *
     * 表示：
     * 1. 每秒生成 100 个令牌
     * 2. 桶最多存 200 个令牌
     * 3. 每个请求消耗 permits 个令牌
     *
     * 适合秒杀接口，因为它可以允许一定突发流量，
     * 但又能限制长期平均请求速度。
     */
    private boolean tokenBucketLimit(String key, RateLimit rateLimit) {

        // 创建 Redis Lua 脚本对象
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        // 设置Lua脚本内容
        script.setScriptText(TOKEN_BUCKET_SCRIPT);

        // 设置脚本返回值类型
        script.setResultType(Long.class);

        // 当前时间戳，单位毫秒
        // Lua 中会根据 now - timestamp 计算这段时间应该补充多少令牌
        long now = System.currentTimeMillis();

        /**
         * Redis 中令牌桶 key 的过期时间
         *
         * 这个 key 不能一直存在，否则 Redis 里会有很多无用的限流 key。
         *
         * capacity / rate 表示桶从空到满大概需要几秒。
         * 乘以 2 是为了让 key 保留得稍微久一点。
         * 最小 60 秒，避免过期太快。
         */
        long ttl = Math.max(
                60,
                rateLimit.capacity() / Math.max(1, rateLimit.rate()) * 2
        );

        // 执行 Lua 脚本
        Long result = stringRedisTemplate.execute(
                script,

                // KEYS[1]：限流key
                Collections.singletonList(key),

                // ARGV[1]：桶容量
                String.valueOf(rateLimit.capacity()),

                // ARGV[2]：每秒生成令牌数量
                String.valueOf(rateLimit.rate()),

                // ARGV[3]：当前时间戳
                String.valueOf(now),

                // ARGV[4]：本次请求消耗令牌数
                String.valueOf(rateLimit.permits()),

                // ARGV[5]：Redis key 过期时间
                String.valueOf(ttl)
        );

        // Lua 返回 1 表示允许通过，返回 0 表示被限流
        return result != null && result == 1;
    }

    /**
     * 构造限流 key
     *
     * 为什么要构造 key？
     *
     * 因为不同接口、不同用户应该有不同的限流维度。
     *
     * 例如普通接口按用户限流：
     *
     * rate_limit:FIXED_WINDOW:/shop/1:user:1001
     *
     * 表示：
     * 用户 1001 访问 /shop/1 这个接口的固定窗口限流计数。
     *
     * 例如秒杀接口全局限流：
     *
     * rate_limit:TOKEN_BUCKET:/voucher-order/seckill:global
     *
     * 表示：
     * 所有用户共同使用这个秒杀接口的同一个令牌桶。
     */
    private String buildLimitKey(RateLimit rateLimit) {

        // 获取当前 HTTP 请求对象
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        HttpServletRequest request = attributes.getRequest();

        // 当前请求 URI，例如 /shop/1、/voucher-order/seckill/10
        String uri = request.getRequestURI();

        /**
         * 如果注解中手动指定了 key，就用注解里的 key。
         * 如果没指定，就默认使用请求 URI 作为业务 key。
         */
        String businessKey = StrUtil.isBlank(rateLimit.key())
                ? uri
                : rateLimit.key();

        StringBuilder key = new StringBuilder();

        // key 前缀 + 限流类型 + 业务 key，例如：rate_limit:FIXED_WINDOW:/shop/1
        key.append("rate_limit:")
                .append(rateLimit.type())
                .append(":")
                .append(businessKey);

        /**
         * byUser = true：
         * 按用户限流，每个用户有自己的计数器或令牌桶。
         *
         * byUser = false：
         * 全局限流，所有用户共用一个计数器或令牌桶。
         */
        if (rateLimit.byUser()) {
            key.append(":")
                    .append(getUserOrIp(request)); // 例如 rate_limit:FIXED_WINDOW:/shop/1: user:1001
        } else {
            key.append(":global"); // 例如 rate_limit:FIXED_WINDOW:/shop/1:global
        }

        return key.toString();
    }

    /**
     * 获取当前用户标识
     *
     * 优先使用登录用户 ID。
     * 如果用户没有登录，就使用 IP 地址。
     *
     * 这样既支持登录后的用户级限流，
     * 也支持未登录接口的 IP 限流。
     */
    private String getUserOrIp(HttpServletRequest request) {

        // 1. 优先从 UserHolder 获取当前登录用户
        try {
            UserDTO user = UserHolder.getUser();
            if (user != null && user.getId() != null) {
                return "user:" + user.getId();
            }
        } catch (Exception ignored) {
            // 如果当前请求没有登录用户，忽略异常，继续走 IP 限流
        }

        /**
         * 2. 获取真实 IP
         *
         * 如果项目经过 Nginx、网关、负载均衡，
         * request.getRemoteAddr() 拿到的可能是网关 IP，
         * 所以优先从请求头中取真实客户端 IP。
         */

        // X-Forwarded-For 可能长这样：
        // clientIp, proxy1, proxy2
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip)) {
            return "ip:" + ip.split(",")[0];
        }

        // 有些代理会使用 X-Real-IP
        ip = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(ip)) {
            return "ip:" + ip;
        }

        // 兜底，直接使用远程地址
        return "ip:" + request.getRemoteAddr();
    }
}