package com.hmdp.dto;

import lombok.Data;

/**
 * 发表评论请求。用户身份始终从登录上下文获取，禁止由前端传入。
 */
@Data
public class BlogCommentCreateDTO {
    private Long blogId;
    private String content;
    private String images;
    /** 被回复的评论ID；发表一级评论时为空 */
    private Long replyToId;
}
