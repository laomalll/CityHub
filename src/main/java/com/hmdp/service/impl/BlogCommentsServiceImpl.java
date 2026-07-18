package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.BlogCommentCreateDTO;
import com.hmdp.dto.BlogCommentDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final String COMMENT_LIKED_KEY_PREFIX = "comment:liked:";

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createComment(BlogCommentCreateDTO request) {
        if (request == null || request.getBlogId() == null) {
            return Result.fail("笔记ID不能为空");
        }
        String content = StrUtil.trim(request.getContent());
        if (StrUtil.isBlank(content)) {
            return Result.fail("评论内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Result.fail("评论内容不能超过500字");
        }
        String images = StrUtil.trim(request.getImages());
        if (StrUtil.isNotBlank(images)
                && (!images.startsWith("/imgs/comments/") || images.length() > 1024)) {
            return Result.fail("评论图片路径不合法");
        }
        if (blogMapper.selectById(request.getBlogId()) == null) {
            return Result.fail("笔记不存在");
        }

        long parentId = 0L;
        long answerId = 0L;
        if (request.getReplyToId() != null) {
            BlogComments target = getById(request.getReplyToId());
            if (target == null || !request.getBlogId().equals(target.getBlogId())) {
                return Result.fail("被回复的评论不存在");
            }
            parentId = target.getParentId() == null || target.getParentId() == 0L
                    ? target.getId() : target.getParentId();
            answerId = target.getId();
        }

        UserDTO loginUser = UserHolder.getUser();
        BlogComments comment = new BlogComments()
                .setUserId(loginUser.getId())
                .setBlogId(request.getBlogId())
                .setParentId(parentId)
                .setAnswerId(answerId)
                .setContent(content)
                .setImages(StrUtil.isBlank(images) ? null : images)
                .setLiked(0)
                .setStatus(false);
        if (!save(comment)) {
            return Result.fail("发表评论失败");
        }

        LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Blog::getId, request.getBlogId())
                .setSql("comments = COALESCE(comments, 0) + 1");
        if (blogMapper.update(null, updateWrapper) != 1) {
            throw new IllegalStateException("更新笔记评论数失败");
        }
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryCommentsByBlogId(Long blogId) {
        if (blogId == null) {
            return Result.fail("笔记ID不能为空");
        }
        List<BlogComments> comments = lambdaQuery()
                .eq(BlogComments::getBlogId, blogId)
                .and(wrapper -> wrapper.eq(BlogComments::getStatus, false)
                        .or().isNull(BlogComments::getStatus))
                .orderByAsc(BlogComments::getCreateTime)
                .list();
        if (comments.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIds = comments.stream().map(BlogComments::getUserId)
                .distinct().collect(Collectors.toList());
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, BlogComments> commentMap = comments.stream()
                .collect(Collectors.toMap(BlogComments::getId, Function.identity()));
        Map<Long, BlogCommentDTO> dtoMap = comments.stream().collect(Collectors.toMap(
                BlogComments::getId,
                comment -> toDTO(comment, users, commentMap)
        ));

        comments.stream()
                .filter(comment -> comment.getParentId() != null && comment.getParentId() != 0L)
                .forEach(comment -> {
                    BlogCommentDTO parent = dtoMap.get(comment.getParentId());
                    if (parent != null) parent.getReplies().add(dtoMap.get(comment.getId()));
                });
        List<BlogCommentDTO> result = comments.stream()
                .filter(comment -> comment.getParentId() == null || comment.getParentId() == 0L)
                .sorted((left, right) -> right.getCreateTime().compareTo(left.getCreateTime()))
                .map(comment -> dtoMap.get(comment.getId()))
                .collect(Collectors.toList());
        return Result.ok(result);
    }

    private BlogCommentDTO toDTO(BlogComments comment, Map<Long, User> users,
                                 Map<Long, BlogComments> commentMap) {
        BlogCommentDTO dto = new BlogCommentDTO();
        dto.setId(comment.getId());
        dto.setUserId(comment.getUserId());
        dto.setContent(comment.getContent());
        dto.setImages(comment.getImages());
        dto.setLiked(comment.getLiked() == null ? 0 : comment.getLiked());
        dto.setCreateTime(comment.getCreateTime());

        UserDTO loginUser = UserHolder.getUser();
        dto.setIsLike(loginUser != null && stringRedisTemplate.opsForZSet().score(
                COMMENT_LIKED_KEY_PREFIX + comment.getId(), loginUser.getId().toString()) != null);
        User user = users.get(comment.getUserId());
        if (user != null) {
            dto.setUserName(user.getNickName());
            dto.setUserIcon(user.getIcon());
        }
        if (comment.getAnswerId() != null && comment.getAnswerId() != 0L) {
            BlogComments answered = commentMap.get(comment.getAnswerId());
            User answeredUser = answered == null ? null : users.get(answered.getUserId());
            if (answeredUser != null) dto.setReplyToUserName(answeredUser.getNickName());
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result likeComment(Long commentId) {
        if (commentId == null || getById(commentId) == null) {
            return Result.fail("评论不存在");
        }
        Long userId = UserHolder.getUser().getId();
        String key = COMMENT_LIKED_KEY_PREFIX + commentId;
        Boolean added = stringRedisTemplate.opsForZSet()
                .addIfAbsent(key, userId.toString(), System.currentTimeMillis());
        if (Boolean.TRUE.equals(added)) {
            update().eq("id", commentId).setSql("liked = COALESCE(liked, 0) + 1").update();
            return Result.ok(true);
        }
        stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        update().eq("id", commentId)
                .setSql("liked = CASE WHEN COALESCE(liked, 0) > 0 THEN liked - 1 ELSE 0 END")
                .update();
        return Result.ok(false);
    }
}
