-- 订单创建失败时，利用Lua脚本完成Redis库存回滚、用户一人一单标记抹除
-- 0. 参数
local voucherId = ARGV[1] -- 优惠券ID
local userId = ARGV[2] -- 用户ID

-- 1. Key
-- 优惠券key，value是hash类型，存储该优惠券的库存、开始抢购时间、结束抢购时间
local seckillInfoKey = 'seckill:info:' .. voucherId  -- 库存信息
-- 订单key，value是set类型，存储着已下过单的用户id
local orderKey = 'seckill:vid:' .. voucherId -- 一人一单信息

-- 2. 判断用户是否存在抢购标记
if (redis.call('SISMEMBER', orderKey, userId) == 0) then
    -- 用户没有抢购标记，说明已经回滚过或数据异常
    return 1
end

-- 3. 回滚库存
redis.call('HINCRBY', seckillInfoKey, 'stock', 1)

-- 4. 删除一人一单标记
redis.call('SREM', orderKey, userId)

return 0