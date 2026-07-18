package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // JSON序列化工具
    private static final ObjectMapper mapper = new ObjectMapper();


    /**
     * 发送给手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) {
            return Result.fail("手机号格式错误！！");
        }
        // 2. 随机生成6位的验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 保存验证码到Redis中，有效期为2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4. 发送验证码
        log.info("发送短信验证码成功，验证码：" + code);

        // 5. 返回结果
        return Result.ok();
    }

    /**
     * 登录和注册功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误！！");
        }
        // 2. 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone()); // 服务器发送的验证码
        String code = loginForm.getCode(); // 用户填写的验证码
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！！");
        }

        // 3. 根据手机号查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, loginForm.getPhone()));

        // 4. 判断用户是否已注册，未注册则注册
        if (user == null) {
            // 创建新用户
            user = User.builder()
                    .phone(loginForm.getPhone()) // 手机号
                    .createTime(LocalDateTime.now()) // 创建时间
                    .updateTime(LocalDateTime.now()) // 修改时间
                    .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10)) // 生成随机昵称
                    .build();
            userMapper.insert(user); // 注册用户
        }

        // 5. 保存用户到信息到Redis中
        String token = null;
        try {
            // 5.1 为用户生成token
            token = UUID.randomUUID().toString(true);
            // 5.2 手动序列化：userDTO -> JSON字符串
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class); // 数据拷贝
            String userDTOJson = mapper.writeValueAsString(userDTO); // 手动序列化
            // 5.3 存储到Redis中
            String tokenKey = LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForValue().set(tokenKey,userDTOJson);
            // 5.4 给tokenKey设置有效期
            stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES); // 30分钟的有效期

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // 登录成功，要把短信验证码从Redis中删除，保证只使用一次
        stringRedisTemplate.delete(LOGIN_CODE_KEY + loginForm.getPhone());

        // 6. 响应token给前端
        return Result.ok(token);

    }

    /**
     * 登录：通过手机号 + 密码登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result loginByPassword(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误！！");
        }

        // 2. 根据手机号查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, loginForm.getPhone()));

        // 3. 判断用户是否存在
        if (user == null) {
            return Result.fail("该手机号尚未注册，请先完成注册！");
        }

        // 4. 校验密码（这里我们不校验密码）
//        if (!loginForm.getPassword().equals(user.getPassword())) {
//            return Result.fail("密码错误！");
//        }

        // 5. 保存用户到信息到Redis中
        String token = null;
        try {
            // 5.1 为用户生成token
            token = UUID.randomUUID().toString(true);
            // 5.2 手动序列化：userDTO -> JSON字符串
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class); // 数据拷贝
            String userDTOJson = mapper.writeValueAsString(userDTO); // 手动序列化
            // 5.3 存储到Redis中
            String tokenKey = LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForValue().set(tokenKey,userDTOJson);
            // 5.4 给tokenKey设置有效期
            stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES); // 30分钟的有效期

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // 6. 响应token给前端
        return Result.ok(token);
    }

    /**
     * 用户签到
     * @return
     */
    @Override
    public Result sign() {
        // 1、获取当前登录用户
        UserDTO loginUser = UserHolder.getUser();
        if (loginUser == null){
            return Result.fail("请先登录！");
        }
        // 2、获取日期
        LocalDateTime now = LocalDateTime.now();
        String key = "sign:" + loginUser.getId() + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth(); // 今天是本月的第几天

        // 3、向指定位置插入1 SETBIT key offset value
        stringRedisTemplate.opsForValue().setBit(
                key, // key
                dayOfMonth - 1, // offset
                true // value
        );

        // 4、返回结果
        return Result.ok();

    }

    /**
     * 统计登录用户截止到当天在本月的连续签到天数
     */
    @Override
    public Result signCount() {
        // 1、获取当前登录用户
       UserDTO loginUser = UserHolder.getUser();

        // 2、获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String key = "sign:" + loginUser.getId() + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth(); // 今天是本月的第几天

        // 3、获取本月中到今天的所有签到数据,BITFIELD key GET type offset 返回的是一个十进制的数字 result.get(0)
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType
                                .unsigned(dayOfMonth)) // type：udayOfMonth
                                .valueAt(0) // offset
        );
        // 没有任何签到结果
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        // 4、获取返回结果：返回的是一个十进制数字
        Long num = result.get(0);
        if (num == null || num == 0L){
            return Result.ok(0);
        }

        // 5、循环遍历
        int count = 0; // 记录截止到今天的连续签到次数
        while (true){
            // 5.1 让num与1做与运算，得到num的最后一个bit位
            if ((num & 1) ==1){ // 说明已签到
                count++;  // 计数器加1
            }else{ // 5.2 若最后一位为0，说明未签到，直接可以退出了，因为要统计的是截止到今天为止的连续签到次数
                break;
            }
            num = num >> 1;
        }

        // 5、返回结果
        return Result.ok(count);
    }
}
