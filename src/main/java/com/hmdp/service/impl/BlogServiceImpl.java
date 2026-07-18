package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private FollowMapper followMapper;

    /**
     * 查看探店博文
     * @param id 博文id
     * @return
             */
    @Override
    public Result queryBlogById(Long id) {
        // 1、查询博文
        LambdaQueryWrapper<Blog> blogWrapper = new LambdaQueryWrapper<>();
        blogWrapper.eq(Blog::getId, id);
        Blog blog = blogMapper.selectOne(blogWrapper);
        if (blog == null){
            return Result.fail("博文不存在");
        }

        // 2、查询博文的作者信息
        Long userId = blog.getUserId();
        User user = userMapper.selectById(userId);
        blog.setName(user.getNickName()); // 设置博文的作者昵称
        blog.setIcon(user.getIcon()); //设置博文的作者头像

        // 3、判断当前登录用户是否已对该博文点赞
        UserDTO loginUser = UserHolder.getUser(); // 获取当前登录用户
        if (loginUser != null) {
            Long currentUserId = loginUser.getId(); // 当前用户id
            String key = "blog:liked:" + id;
            Double score = stringRedisTemplate.opsForZSet().score(key, currentUserId.toString()); // 获取当前用户点赞该博文的分数，若为null，说明未点赞
            blog.setIsLike(score != null); // 设置当前用户是否点赞 true：已点赞 false：未点赞
        }

        // 4、返回结果
        return Result.ok(blog);
    }

    /**
     * 点赞博文
     * @param id 博文id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1. 获取当前用户
        UserDTO user = UserHolder.getUser();

        // 2. 判断当前用户是否已经点赞
        String key  = "blog:liked:" + id;
        // ZSet是不重复 + 可排序 集合，成功返回true（未点赞），已存在返回false（已点赞）
        Boolean success= stringRedisTemplate.opsForZSet().addIfAbsent(key, user.getId().toString(), System.currentTimeMillis()); // 当集合不存在时会创建

        // 3 确定操作是点赞还是取消点赞
        if (success == true){ // 未点赞，说明是点赞
            // 3.1 点赞数量 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").
                    eq("id", id)
                    .update();
        }else {
            // 4. 已点赞，再次点击说明是取消点赞
            // 4.1 数据库点赞数量 - 1
            boolean isSuccess = update().setSql("liked = liked - 1").
                    eq("id", id)
                    .update();
            // 4.2 从Redis中该博文的点赞用户集合中移除该用户id
            stringRedisTemplate.opsForZSet().remove(key, user.getId().toString());
        }
        return Result.ok();
    }



//    /**
//     * 点赞博文
//     * @param id 博文id
//     * @return
//     */
//    @Override
//    public Result likeBlog1(Long id) {
//        // 1. 获取当前用户
//        UserDTO user = UserHolder.getUser();
//
//        // 2、这里应该上锁，否则并发情况下操作数据库会出现一个用户多次点赞
//        // 锁的功能：同一用户互斥，不同用户互不干扰
//        String lockKey = "lock:blog:" + id + user.getId(); // 锁的key
//        RLock lock = redissonClient.getLock(lockKey); // 获取锁对象
//        // 尝试获取锁
//        if (!lock.tryLock()){ // 获取锁失败
//            // 获取锁失败，返回错误
//            return Result.fail("请不要恶意攻击");
//        }
//
//        // 获取锁成功
//        try{
//            // 3. 判断当前用户是否已经点赞
//            String key  = "blog:liked:" + id;
//            // Set是不重复集合，成功返回1（未点赞），已存在返回0（已点赞）
//            Long success = stringRedisTemplate.opsForSet().add(key, user.getId().toString()); // 当集合不存在时会创建
//
//            // 4. 未点赞，则说明当前操作是是点赞
//            if (success == 1){ // 未点赞
//                // 数据库中点赞数量 + 1
//                boolean isSuccess = update().setSql("liked = liked + 1").
//                        eq("id", id)
//                        .update();
//
//            }else {
//                // 5. 已点赞，再次点击说明是取消点赞
//                // 5.1 数据库点赞数量 - 1
//                boolean isSuccess = update().setSql("liked = liked - 1").
//                        eq("id", id)
//                        .update();
//                // 5.2 从Redis中该博文的点赞用户集合中移除该用户id
//                stringRedisTemplate.opsForSet().remove(key, user.getId().toString());
//            }
//        }finally {
//            // 释放锁
//            lock.unlock();
//        }
//
//        // 6. 返回结果
//        return Result.ok();
//    }

    /**
     * 查询点赞数量最多的博文
     * @param current 页码
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current, Integer pageSize) {
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null ? 9 : Math.min(Math.max(pageSize, 1), 50);
        // 1. 根据用点赞数量查询，首推点赞数量多的博文
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .orderByDesc("id")
                .page(new Page<>(safeCurrent, safePageSize));
        // 2. 获取当前页数据
        List<Blog> records = page.getRecords();

        // 3. 博文信息设置
        records.forEach(blog -> {
            // 3.1 查询每篇博文的作者信息
            Long userId = blog.getUserId(); // 博文的作者id
            User user = userMapper.selectById(userId);// 查询作者信息
            blog.setName(user.getNickName()); // 设置作者昵称
            blog.setIcon(user.getIcon()); // 设置作者头像

            // 3.2 查询每篇博文当前登录用户是否点赞，设置isLike属性
            UserDTO loginUser = UserHolder.getUser(); // 获取当前登录用户
            if (loginUser != null){
                Long currentUserId = loginUser.getId(); // 当前用户id
                String key = "blog:liked:" + blog.getId();
                Double score = stringRedisTemplate.opsForZSet().score(key, currentUserId.toString()); // 获取当前用户点赞该博文的分数，若为null，说明未点赞
                blog.setIsLike(score !=null);// 设置当前用户是否点赞 true：已点赞 false：未点赞
            }
        });

        // 4. 返回分页信息，供前端渲染页码
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("current", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        return Result.ok(result);
    }

    /**
     * 查询博文点赞列表，TOP5
     * @param id 博文id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {

        // 1. 从Redis中获取该博文的点赞用户集合
        String key = "blog:liked:" + id;
        Set<String> top5_ids = stringRedisTemplate.opsForZSet().range(key, 0, 4); // 用户的id集合TOP5（按点赞时间升序排序）
        if (top5_ids == null || top5_ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2. 将ids转为id数组
        List<Long> userIds = top5_ids.stream().map(Long::valueOf).collect(Collectors.toList());

        // 3. 从数据库查询这些用户的信息
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds); // 这里需要注意，数据库并不会按照你传入的ID顺序来返回用户数据
        List<User> userList = userMapper.selectList(userWrapper);

        // 4、将userList按ids的顺序排序
        userList.sort(new Comparator<User>() {
            @Override
            public int compare(User o1, User o2) {
                return userIds.indexOf(o1.getId()) - userIds.indexOf(o2.getId());
            }
        });

        // 5、将密码置为空
        userList.forEach(user -> {
            user.setPassword(null);
        });

        // 6、返回结果
        return Result.ok(userList);
    }

    /**
     * 发布探店博文
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取当前登录用户
        UserDTO loginUser = UserHolder.getUser();
        blog.setUserId(loginUser.getId()); // 设置用户id

        // 2.保存探店博文
        blogMapper.insert(blog);
        long now = System.currentTimeMillis(); // 代表发布的时间

        // 3.查询关注的所有粉丝
        LambdaQueryWrapper<Follow> followWrapper = new LambdaQueryWrapper<>();
        followWrapper.eq(Follow::getFollowUserId, loginUser.getId());
        List<Follow> followList = followMapper.selectList(followWrapper); // 所有的粉丝数据
        if (followList == null || followList.isEmpty()){ // 如果没有粉丝，直接退出
            return Result.ok(blog.getId());
        }

        // 4.推送博文到粉丝的Redis中
        for (Follow follow : followList) {
            Long userId = follow.getUserId(); // 粉丝userId
            String key = "feed:" + userId; // 粉丝收件箱的key
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), now); // 推送博客id到粉丝的收件箱
        }

        // 5.返回探店博文的id
        return Result.ok(blog.getId());
    }

    /**
     * 查询当前用户所关注的博主的最新博文
     * @param max 上次查询的最后一条记录的时间戳
     * @param offset 偏移量，offset代表要从max后几位开始，0则包含max，极端情况下有多个时间戳相同
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前登录用户
        UserDTO loginUser = UserHolder.getUser();
        String key = "feed:" + loginUser.getId();
        long pageSize = 2;

        // 2、从用户的收件箱中获取博文数据：ZREVRANGEBYSCORE key max min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, pageSize); // 这里需要调用reverse开头的API，因为默认ZSet按升序排序，所以需要调用reverseRangeByScore

        // 3、判断是否为空
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }

        // 4、解析数据
        List<Long> ids = new ArrayList<>(typedTuples.size()); // 存储博文id
        long minTime = 0; // 存储时间戳
        int os = 1; // 存储偏移量
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 4.1 获取博文id
            String id = typedTuple.getValue();
            ids.add(Long.valueOf(id));

            // 4.2 获取博文的时间戳
            long time = typedTuple.getScore().longValue();
            if (time == minTime){
                os ++;
            }else{
                minTime = time;
                os = 1; // 重置偏移量
            }
        }

        // 还要再判断一下minTime和上一次查询结果的max是否一致
        if (minTime == max){ // 若minTime和上次查询结果的max一致，则偏移量还要修改
            os = os + offset;
        }

        // 5、根据id查询博文数据
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Blog::getId, ids);
        List<Blog> blogList = blogMapper.selectList(wrapper); // Mysql的查询数据不会按照ids的顺序返回

        // 6、对博文按照前面的ids集合顺序进行排序
        blogList.sort(new Comparator<Blog>() {
            @Override
            public int compare(Blog o1, Blog o2) {
                return ids.indexOf(o1.getId()) - ids.indexOf(o2.getId());
            }
        });

        // 7、设置一下博文的作者图标icon、昵称nickName、当前登录用户是否已点赞属性isLiked
        blogList.forEach(blog -> {
            // 7.1 查询博文作者
            User user = userMapper.selectById(blog.getUserId());
            blog.setIcon(user.getIcon());
            blog.setName(user.getNickName());

            // 7.2 判断当前登录用户是否对博文进行点赞
            Long currentUserId = loginUser.getId(); // 当前用户id
            Double score = stringRedisTemplate.opsForZSet().score("blog:liked:" + blog.getId(), currentUserId.toString()); // 获取当前用户点赞该博文的分数，若为null，说明未点赞
            blog.setIsLike(score !=null);// 设置当前用户是否点赞 true：已点赞 false：未点赞
        });

        // 8、封装结果并返回
        return Result.ok(new ScrollResult(blogList, minTime, os));

    }
}
