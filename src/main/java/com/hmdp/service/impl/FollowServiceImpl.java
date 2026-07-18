package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private FollowMapper followMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;
    @Autowired
    private BlogMapper blogMapper;

    /**
     * 关注 or 取消关注
     * @param followUserId 关注 or 取消关注的用户id
     * @param isFollow false：取消关注  true：关注
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;

        // 2、判断是否关注
        if (isFollow){ // 关注
            Follow follow = Follow.builder()
                    .userId(userId)
                    .followUserId(followUserId)
                    .createTime(LocalDateTime.now())
                    .build();
            // 添加关注记录到数据库
            followMapper.insert(follow); // 添加关注记录到数据库

            // 添加关注的用户id到Redis
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());

        }else{ // 取消关注
            LambdaQueryWrapper<Follow> followWrapper = new LambdaQueryWrapper<>();
            followWrapper.eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId);
            // 删除数据库中的关注记录
            followMapper.delete(followWrapper);

            // 删除Redis中的关注用户id
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());

            // 删除Redis中，当前登录用户的收件箱中该博主的所有探店笔记
            LambdaQueryWrapper<Blog> blogWrapper = new LambdaQueryWrapper<>();
            blogWrapper.eq(Blog::getUserId, followUserId);
            List<Blog> blogList = blogMapper.selectList(blogWrapper);  // 该博主的所有探店笔记
            blogList.forEach(blog -> { // 从当前登录用户的收件箱中删除
                stringRedisTemplate.opsForZSet().remove("feed:" + userId, blog.getId().toString());
            });
        }

        // 3、返回结果
        return Result.ok();
    }

    /**
     * 判断当前用户是否关注了博主
     * @param followUserId 博主id
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 1.判断用户是否登录
        if (UserHolder.getUser() == null){
            return Result.ok(false); // 显示关注
        }

        // 2、获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 3.查询是否关注 select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        // 4.判断
        return Result.ok(count > 0);
    }

    /**
     * 查询共同关注的用户
     * @param followUserId 关注的用户id
     * @return
     */
    @Override
    public Result followCommons(Long followUserId) {
        // 1.获取当前登录用户id
        Long loginUserId = UserHolder.getUser().getId();

        // 2.设置key
        String key1 = "follow:" + loginUserId;
        String key2 = "follow:" + followUserId;

        // 3、创建两个Set集合的交集，即共同关注
        Set<String> ids = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (ids == null || ids.isEmpty()){
            return Result.ok(new ArrayList<>());
        }

        // 4、转换为Long类型id
        List<Long> userIds = ids.stream().map(Long::valueOf).collect(Collectors.toList());

        // 5、查询共同关注用户
        List<User> userList = userMapper.selectBatchIds(userIds);

        // 6、转换为UserDTO
        List<UserDTO> users = userList
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 7、返回结果
        return Result.ok(users);

    }
}
