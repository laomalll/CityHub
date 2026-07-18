package com.hmdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.message.VoucherOrderMessage;
import com.hmdp.utils.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

// RabbitMQ的配置信息

@Slf4j
@Configuration
public class MqConfig {

    // 配置消息转换器
    @Bean
    public Jackson2JsonMessageConverter messageConverter(){
        // 1.定义消息转换器
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter();
        // 2.配置自动创建消息id，用于识别不同消息，也可以在业务中基于ID判断是否是重复消息
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }


    // 创建order.error.direct交换机
    @Bean
    public DirectExchange orderErrorDirect() {
        return new DirectExchange(MqConstants.EXCHANGE_ORDER_ERROR);
    }

    // 创建order.error.queue队列，存放创建订单失败的消息
    @Bean
    public Queue orderErrorQueue() {
        return new Queue(MqConstants.QUEUE_ORDER_ERROR);
    }

    // 队列与交换机绑定，routingKey = createOrderError
    @Bean
    public Binding errorBinding(Queue orderErrorQueue, DirectExchange orderErrorDirect){
        return BindingBuilder.bind(orderErrorQueue).to(orderErrorDirect).with("createOrderError");
    }

    // 定义RepublishMessageRecoverer，关联队列和交换机
    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate,
                                             StringRedisTemplate stringRedisTemplate,
                                             ObjectMapper objectMapper){
        // 回滚Redis库存、抹除用户一人一单标记的lua脚本，Long为脚本返回值
        DefaultRedisScript<Long> refundScript = new DefaultRedisScript<>();
        refundScript.setLocation(new ClassPathResource("returnStock.lua")); // 设置脚本路径
        refundScript.setResultType(Long.class); // 设置脚本返回值类型

        // 创建RepublishMessageRecoverer，关联队列和交换机
        RepublishMessageRecoverer republishRecoverer =
                new RepublishMessageRecoverer(rabbitTemplate, MqConstants.EXCHANGE_ORDER_ERROR, "createOrderError");

        return new MessageRecoverer() {
            @Override
            public void recover(Message message, Throwable cause) {
                log.error("消费者重试耗尽，开始Redis库存回滚，消息ID：{}", message.getMessageProperties().getMessageId());
                try {
                    // 获取发生异常的订单消息
                    VoucherOrderMessage orderMsg = objectMapper.readValue(message.getBody(), VoucherOrderMessage.class);
                    // 执行lua脚本
                    stringRedisTemplate.execute(
                            refundScript, // lua脚本
                            Collections.emptyList(),
                            orderMsg.getVoucherId().toString(), orderMsg.getUserId().toString() // 传入查参数
                    );
                    log.info("Redis库存回滚成功，userId={}, voucherId={}", orderMsg.getUserId(), orderMsg.getVoucherId());
                } catch (Exception e) {
                    log.error("Redis库存回滚失败：{}", e.getMessage());
                }
                // 将重试耗尽后仍然失败的消息，转发到错误队列（order.error.queue）中保存起来，后续可由人工介入处理
                republishRecoverer.recover(message, cause);
            }
        };
    }

}
