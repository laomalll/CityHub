-- 0. 参数列表
local voucherId = ARGV[1] -- 优惠券ID
local userId = ARGV[2] -- 用户ID
local now = tonumber(ARGV[3]) -- 当前时间戳(秒)

-- 1. 数据key
-- 优惠券key，value是hash类型，存储该优惠券的库存、开始抢购时间、结束抢购时间
local seckillInfoKey = 'seckill:info:' .. voucherId -- .. 代表lua语法中的拼接
-- 订单key，value是set类型，存储着已下过单的用户id
local orderKey = 'seckill:vid:' .. voucherId

-- 2. 获取优惠券完整信息
local voucher = redis.call('HMGET', seckillInfoKey, 'stock', 'beginTime', 'endTime')

-- 3. 解析数据（Redis返回字符串，需转数字）
local stock = tonumber(voucher[1])       -- 库存数量
local beginTime = tonumber(voucher[2])  -- 开始抢购时间的时间戳
local endTime = tonumber(voucher[3])    -- 抢购结束时间的时间戳

-- 4. 时间/库存/重复抢购判断
if (now < beginTime) then
    return 3  -- 抢购未开始
end
if (now > endTime) then
    return 3  -- 抢购已结束
end
if (stock <= 0) then
    return 1  -- 库存不足，返回1
end

-- 5. 判断用户是否是重复抢购
if (redis.call('SISMEMBER', orderKey, userId) == 1) then
    return 2  -- 存在，说明是重复抢购
end

-- 6、走到这里，说明所有条件满足：有抢购资格
-- 6.1 扣减优惠券的库存
redis.call('HINCRBY', seckillInfoKey, 'stock', -1)
-- 6.2 添加用户ID到已抢购的set集合中
redis.call('SADD', orderKey, userId)
-- 6.3 发送消息到队列中，XADD stream.orders * key1 value1 key2 value2 ....
--redis.call('XADD', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId)

return 0 -- 抢购成功，返回0