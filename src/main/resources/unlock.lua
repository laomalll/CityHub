-- 获取锁中的value，这里的KEYS[1]就是锁的key，ARGV[1]就是当前线程标识
local result = redis.call('GET', KEYS[1])

-- 判断锁中的value是否与当前线程标识一致
if result == ARGV[1] then
    -- 一致,删除锁
    redis.call('DEL', KEYS[1])
    return 1
else
   -- 不一致，直接返回
    return 0
end

