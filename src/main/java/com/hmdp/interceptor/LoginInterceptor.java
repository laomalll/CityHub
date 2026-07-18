package com.hmdp.interceptor;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 校验登录状态
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 判断ThreadLocal中是否有用户信息，如果无，则拦截
        if (UserHolder.getUser() == null){
            log.debug("用户未登录 or token失效, 请重新登录");
            response.setStatus(401);
            return false;
        }

        // 2. ThreadLocal中有用户信息，说明已登录，且token未过期，直接放行
        return true;
    }
}
