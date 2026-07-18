package com.hmdp.interceptor;


import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Component
@Slf4j
public class AllInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // JSON序列化工具
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){ // 如果token为空，则直接放行，后续由登录拦截器处理
            return true;
        }

        // 2.利用token从redis中获取用户信息
        String tokenKey = LOGIN_USER_KEY + token;
        String userJson = stringRedisTemplate.opsForValue().get(tokenKey); // 获取用户信息
        if (StrUtil.isBlank(userJson)){ // 如果用户信息为空，则直接放行，后续由登录拦截器处理
            return true;
        }
        UserDTO userDTO = mapper.readValue(userJson, UserDTO.class); // 手动反序列化：JSON字符串 -> userDTO


        // 3. 将用户信息存储到ThreadLocal
        UserHolder.saveUser(userDTO);

        // 4. 刷新token的有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5. 放行
        return true;
    }

    // 业务请求结束之后，删除ThreadLocal中的数据，避免内存泄露
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        UserHolder.removeUser();
    }

    // 确保即使发生异常也要清理ThreadLocal
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
