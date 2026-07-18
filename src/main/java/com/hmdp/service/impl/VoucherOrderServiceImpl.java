package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.message.MultiDelayMessage;
import com.hmdp.message.VoucherOrderMessage;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.MqConstants;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;


    @Resource
    private StringRedisTemplate stringRedisTemplate;


    // 判断是否有抢购秒杀券的lua脚本，Long为脚本返回值
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 回滚秒杀券库存、抹除用户一人一单标记的lua脚本，Long为脚本返回值
    private static final DefaultRedisScript<Long> REFUND_SCRIPT;


    // 初始化lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua")); //设置脚本文件
        SECKILL_SCRIPT.setResultType(Long.class); // 设置返回值类型为Long

        REFUND_SCRIPT = new DefaultRedisScript<>();
        REFUND_SCRIPT.setLocation(new ClassPathResource("returnStock.lua")); //设置脚本文件
        REFUND_SCRIPT.setResultType(Long.class); // 设置返回值类型为Long

    }

    /**
     * 秒杀券抢购
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        // 1、执行lua脚本，判断是否有抢购资格
        Long userId = UserHolder.getUser().getId(); // 用户id
        Long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC); // 当前时间戳(秒)
        Long result = stringRedisTemplate.execute( // 执行lua脚本
                SECKILL_SCRIPT, // lua脚本
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),now.toString() // 传入参数
        );

        // 2、判断返回结果，确定是否有抢购资格
        int r = result.intValue();
        if (r == 3){
            return Result.fail("请在时间范围内抢购优惠券");
        }else if (r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 3、有抢购资格,用户id、秒杀券id、订单id发送到消息队列
        Long orderId = redisIdWorker.nextId("order"); // 利用Redis生成全局唯一的订单id
        VoucherOrderMessage msg = VoucherOrderMessage.builder() // 构建消息
                .userId(userId) //用户id
                .voucherId(voucherId) // 优惠券id
                .orderId(orderId) // 订单id
                .build();
        // 生产者发送消息
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_ORDER,MqConstants.ROUTING_KEY_ORDER, msg);

        // 4、返回订单id
        return Result.ok(orderId);
    }

    // 定义一个消费者，监听订单队列，将订单信息写入数据库
    // 如果在执行业务代码过程中遇到异常，会触发消费者失败重试机制，重试3次失败后则转入存放错误信息的队列，并且回滚Redis库存、抹除一人一单标记，具体可看MqConfig类
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.QUEUE_ORDER, durable = "true"),
            exchange = @Exchange(name = MqConstants.EXCHANGE_ORDER, type = ExchangeTypes.DIRECT),
            key = MqConstants.ROUTING_KEY_ORDER
    ))
    public void listenVoucherOrder(VoucherOrderMessage voucherOrderMessage){
        log.info("消费者收到消息：" + voucherOrderMessage);
        // 生成订单信息
        VoucherOrder voucherOrder = VoucherOrder.builder() // 创建订单信息
                .id(voucherOrderMessage.getOrderId()) // 订单id
                .userId(voucherOrderMessage.getUserId()) //用户id
                .voucherId(voucherOrderMessage.getVoucherId()) // 优惠券id
                .createTime(LocalDateTime.now()) //创建时间
                .updateTime(LocalDateTime.now()) //修改时间
                .build();

        // 把订单数据写入数据库，需要用代理对象调用方法，不能直接调用，否则事务注解不生效
        VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy(); // 当前类的代理对象
        log.info("正在生成订单到数据库：" + voucherOrderMessage);
        proxy.createVoucherOrder(voucherOrder); // 生成订单
        log.info("订单生成成功：" + voucherOrderMessage);

        // 发送延迟消息给延迟交换机，延迟查询订单支付状态，延迟时长为5分钟
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_ORDER_DELAY, MqConstants.ROUTING_KEY_ORDER_DELAY, voucherOrderMessage, new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                message.getMessageProperties().setDelay(300000); // 设置延迟时间，300000毫秒即5分钟
                return message;
            }
        });
    }

    // 定义一个消费者，监听延时队列的消息
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.QUEUE_ORDER_DELAY, durable = "true"),
            exchange = @Exchange(name = MqConstants.EXCHANGE_ORDER_DELAY, type = ExchangeTypes.DIRECT,delayed = "true"),
            key = MqConstants.ROUTING_KEY_ORDER_DELAY
    ))
    public void listenDelayVoucherOrder(VoucherOrderMessage voucherOrderMessage){
        log.info("消费者收到延时消息：" + voucherOrderMessage);
        // 1. 查询订单状态
        VoucherOrder voucherOrder = voucherOrderMapper.selectById(voucherOrderMessage.getOrderId());

        // 2. 判断订单状态,1是未支付，大于1则不用处理，2：已支付；3：已核销；4：已取消；5：退款中；6：已退款
        if (voucherOrder == null || voucherOrder.getStatus() > 1){ // 业务幂等性判断，防止消息重复处理
            return;
        }

        // 3. 订单未支付
        if (voucherOrder.getStatus() == 1){
            // 4. 需取消订单、恢复数据库库存、Redis回滚库存、Redis取消一人一单的校验
            log.info("取消订单、恢复数据库库存、Redis回滚库存、Redis取消一人一单的校验：{}", voucherOrder);
            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl) AopContext.currentProxy(); // 当前类的代理对象
            proxy.cancelVoucherOrder(voucherOrder);
        }
    }

    // 订单取消，并恢复相关秒杀券的库存
    @Transactional
    public void cancelVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 取消订单
        LambdaUpdateWrapper<VoucherOrder> VoucherOrderWrapper = new LambdaUpdateWrapper<>();
        VoucherOrderWrapper.set(VoucherOrder::getStatus,4) // 修改订单状态为4：已取消
                .set(VoucherOrder::getUpdateTime,LocalDateTime.now())
                .eq(VoucherOrder::getId, voucherOrder.getId());
        voucherOrderMapper.update(null, VoucherOrderWrapper);


        // 2. 恢复库存
        LambdaUpdateWrapper<SeckillVoucher> seckillVoucherwrapper = new LambdaUpdateWrapper<>();
        seckillVoucherwrapper.setSql("stock = stock + 1") // 恢复库存
                .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId());
        seckillVoucherMapper.update(null, seckillVoucherwrapper);

        // 3. 使用lua脚本完成redis库存回滚、用户一人一单标记抹除
        stringRedisTemplate.execute( // 执行lua脚本
                REFUND_SCRIPT, // lua脚本
                Collections.emptyList(),
                voucherOrder.getVoucherId().toString(),voucherOrder.getUserId().toString() // 传入参数
        );

    }

    /**
     * 将订单数据写入到数据库中
     * @param voucherOrder 订单信息
     */
    @Transactional // 涉及多张操作，开启事务
    public void createVoucherOrder(VoucherOrder voucherOrder){
        // 用户ID
        Long userId = voucherOrder.getUserId();
        // 订单ID
        Long orderId = voucherOrder.getId();
        // 优惠券ID
        Long voucherId = voucherOrder.getVoucherId();

        // 这里做个Double Check，双重校验
        // 查询订单
        Long count = query().eq("user_id", userId).eq("voucher_id",voucherId).count();
        // 判断订单是否存在
        if (count > 0) {
            log.info("用户已经购买过一次！");
            return;
        }

        // 扣减秒杀券库存
        LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0) // 仅当库存大于0时才更新
                .setSql("stock = stock -1"); // 数据库中的库存-1
        int success = seckillVoucherMapper.update(null,wrapper);

        // 扣减库存失败
        if (success == 0) {
            log.info("库存不足！");
            return;
        }

        // 订单数据写入数据库
        voucherOrderMapper.insert(voucherOrder);
    }


    /**
     * 秒杀券抢购
     * @param voucherId
     * @return
     */
//    @Override
//    public Result seckillVoucher1(Long voucherId) throws InterruptedException {
//        // 1、查询优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
//
//        // 2、判断是否开始
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀尚未开始");
//        }
//
//        // 3、判断是否结束
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 尚未开始
//            return Result.fail("秒杀已结束");
//        }
//
//        // 4、判断库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            // 库存不足
//            return Result.fail("库存不足");
//        }
//
//        // 5、一人一单
//        long userId = UserHolder.getUser().getId(); // 用户id
//        RLock lock = redissonClient.getLock("lock:order:" + userId); // 获取锁对象
//        boolean isLock = lock.tryLock(50, TimeUnit.MILLISECONDS); // 尝试获取锁：可重试 + 可重入 + 锁自动续期
//
//        // 判断是否获取锁成功
//        if (!isLock){
//            // 获取锁失败
//            return Result.fail("不允许重复下单！！！");
//        }
//
//        // 获取锁成功
//        // 获取代理对象,代理对象调用createVoucherOrder
//        try {
//            VoucherOrderServiceImpl proxy = (VoucherOrderServiceImpl)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId); // 事务提交
//        }finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }



//    @Transactional // 涉及多张操作，开启事务
//    public Result createVoucherOrder(Long voucherId){
//        Long userId = UserHolder.getUser().getId();
//        // 5、一人一单
//        // 5.1 查询订单
//        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        // 5.2 判断订单是否存在
//        if (count > 0) {
//            return Result.fail("用户已经购买过一次!");
//        }
//
//        // 6、扣减库存
//        LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
//        wrapper.eq(SeckillVoucher::getVoucherId, voucherId)
//                .gt(SeckillVoucher::getStock, 0) // 仅当库存大于0时才更新
//                .setSql("stock = stock -1"); // 数据库中的库存-1
//        int success = seckillVoucherMapper.update(null,wrapper);
//        // 扣减库存失败
//        if (success == 0) {
//            return Result.fail("库存不足！");
//        }
//
//        // 7、扣减库存成功，生成订单
//        long orderId = redisIdWorker.nextId("order"); // Redis生成全局唯一ID
//        VoucherOrder voucherOrder = VoucherOrder.builder()
//                .id(orderId)
//                .userId((Long) UserHolder.getUser().getId()) //用户id
//                .voucherId(voucherId) // 优惠券id
//                .createTime(LocalDateTime.now()) //创建时间
//                .updateTime(LocalDateTime.now()) //修改时间
//                .build();
//        voucherOrderMapper.insert(voucherOrder);
//
//        // 8、返回订单ID
//        return Result.ok(orderId);
//    }
}
