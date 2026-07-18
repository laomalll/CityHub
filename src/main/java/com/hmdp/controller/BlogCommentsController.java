package com.hmdp.controller;

import com.hmdp.dto.BlogCommentCreateDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 探店笔记评论接口。
 * TODO：全部由AI实现
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 发表评论，先需要登录。
     */
    @PostMapping
    public Result createComment(@RequestBody BlogCommentCreateDTO request) {
        return blogCommentsService.createComment(request);
    }

    /**
     * 查询指定笔记的全部一级评论，不分页。
     */
    @GetMapping("/of/blog/{blogId}")
    public Result queryComments(@PathVariable Long blogId) {
        return blogCommentsService.queryCommentsByBlogId(blogId);
    }

    /**
     * 点赞或取消点赞评论，需要登录。
     */
    @PutMapping("/like/{commentId}")
    public Result likeComment(@PathVariable Long commentId) {
        return blogCommentsService.likeComment(commentId);
    }
}
