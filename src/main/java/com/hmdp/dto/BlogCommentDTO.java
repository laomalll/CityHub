package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论展示对象，用户信息仅在查询时组装，不冗余存储到评论表。
 */
@Data
public class BlogCommentDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String userIcon;
    private String content;
    private String images;
    private Integer liked;
    private Boolean isLike;
    private String replyToUserName;
    private LocalDateTime createTime;
    private List<BlogCommentDTO> replies = new ArrayList<>();
}
