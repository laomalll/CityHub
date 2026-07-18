package com.hmdp.config;

import com.hmdp.interceptor.AllInterceptor;
import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.utils.JacksonObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.List;


/**
 * 配置类，注册web层相关组件
 */
@Configuration // 标记为配置类
@Slf4j
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    // 注入登录拦截器
    @Resource
    private LoginInterceptor loginInterceptor;

    // 注入全局拦截器
    @Resource
    private AllInterceptor allInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        // 全局拦截器（设置优先级为0）
        registry.addInterceptor(allInterceptor).addPathPatterns("/**").order(0);

        // 登录拦截器（设置优先级为1）
        registry.addInterceptor(loginInterceptor).order(1) // 登录拦截器
                .addPathPatterns("/**") // 拦截路径
                .excludePathPatterns(  // 排除路径
                        // swagger文档
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/favicon.ico",

                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/blog/hot",
                        "/blog/{id}",
                        "/blog/likes/{id}",
                        "/blog-comments/of/blog/{blogId}",
                        "/blog/of/user",
                        "/user/code",
                        "/user/login",
                        "/user/login/password",
                        "/follow/or/not/{id}");
    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
