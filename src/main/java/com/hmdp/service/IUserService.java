package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {


    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录和注册功能
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 登录：通过手机号 + 密码登录
     * @param loginForm
     * @param session
     * @return
     */
    Result loginByPassword(LoginFormDTO loginForm, HttpSession session);

    /**
     * 用户签到
     * @return
     */
    Result sign();

    /**
     * 统计登录用户截止到当天在本月的连续签到天数
     */
    Result signCount();
}
