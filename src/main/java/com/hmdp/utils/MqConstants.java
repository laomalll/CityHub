package com.hmdp.utils;

public interface MqConstants {
    // 定义接收订单信息的交换机、队列、路由键
    String EXCHANGE_ORDER = "order.direct"; // 交换机
    String QUEUE_ORDER = "order.queue"; // 队列
    String ROUTING_KEY_ORDER = "order"; // 路由键

    // 定义接收异常信息的交换机、队列
    String EXCHANGE_ORDER_ERROR = "order.error.direct"; // 接收异常信息的交换机
    String QUEUE_ORDER_ERROR = "order.error.queue"; // 存放异常信息的队列

    // 定义延迟交换机、队列、路由键
    String EXCHANGE_ORDER_DELAY = "order.delay.direct"; // 延迟交换机
    String QUEUE_ORDER_DELAY = "order.delay.queue"; // 延迟队列
    String ROUTING_KEY_ORDER_DELAY = "order.delay"; // 路由键
}
