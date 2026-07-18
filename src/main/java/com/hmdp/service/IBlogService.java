package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查看探店博文
     * @param id 博文id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博文
     * @param id
     * @return
     */
    Result likeBlog(Long id);


    /**
     * 查询点赞数量最多的博文
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current, Integer pageSize);

    /**
     * 查询博文点赞列表（Top5）
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 发布探店博文
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 查询当前用户所关注的博主的最新博文
     * @param max 上次查询的最后一条记录的时间戳
     * @param offset 偏移量
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
