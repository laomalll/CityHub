package com.hmdp.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    /**
     * 发布探店博文
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 查看探店笔记
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 点赞/取消点赞博文
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 分页查询当前登录用户的博文
     * @param current
     * @return
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize) {
        // 1、获取当前登录用户
        UserDTO user = UserHolder.getUser();

        // 2、设置查询条件
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Blog::getUserId, user.getId());

        // 3、设置分页
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null ? 9 : Math.min(Math.max(pageSize, 1), 50);
        Page<Blog> page = new Page<>(safeCurrent, safePageSize);
        wrapper.orderByDesc(Blog::getCreateTime);

        // 4、查询
        blogService.page(page, wrapper);

        // 5、获取当前页数据
        List<Blog> records = page.getRecords();

        // 6、返回完整分页信息
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("current", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        return Result.ok(result);
    }

    /**
     * 查询点赞数量最多的博文
     * @param current
     * @return
     */

    @GetMapping("/hot")
    public Result queryHotBlog(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "9") Integer pageSize) {
        return blogService.queryHotBlog(current, pageSize);
    }


    /**
     * 博客点赞列表查询（Top5）
     *
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }


    /**
     * 根据id查询博主的博文
     * @param current 页码
     * @param id 博主id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {

        // 1、设置查询条件
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Blog::getUserId, id);

        // 2、分页查询
        Page<Blog> page = new Page<>(current, SystemConstants.MAX_PAGE_SIZE);
        blogService.page(page, wrapper);

        // 3、获取数据
        List<Blog> records = page.getRecords();

        // 4、返回结果
        return Result.ok(records);
    }

    /**
     * 查询当前用户所关注的博主的最新博文
     * @param max 上次查询的最后一条记录的时间戳
     * @param offset 偏移量
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max, offset);
    }
}
