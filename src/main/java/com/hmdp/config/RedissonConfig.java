package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean // 注入IOC容器
    public RedissonClient redissonClient(){ // 6379端口的Redis服务
        // 配置类
        Config config = new Config();

        // 添加单节点Redis，调用的是useSingleServer()；如果是集群模式，也可以使用useClusterServers()
        config.useSingleServer()
                .setAddress("redis://192.168.88.131:6379") // Redis服务的IP地址
                .setPassword("123456"); // Redis服务密码

        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
