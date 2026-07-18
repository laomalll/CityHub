package com.hmdp.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VoucherOrderMessage {
    private Long userId; // 用户id
    private Long voucherId; // 秒杀券id
    private Long orderId; // 订单id
}
