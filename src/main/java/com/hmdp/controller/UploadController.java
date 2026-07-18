package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;


/**
 * 文件上传接口
 */


@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    /**
     * 上传文件（点击发布图片，执行该方法）
     * @param image
     * @return
     */
    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        return saveImage(image, "blogs");
    }

    /**
     * 上传评论图片。
     */
    @PostMapping("/comment")
    public Result uploadCommentImage(@RequestParam("file") MultipartFile image) {
        return saveImage(image, "comments");
    }

    private Result saveImage(MultipartFile image, String category) {
        try {
            if (image == null || image.isEmpty() || image.getContentType() == null
                    || !image.getContentType().startsWith("image/")) {
                return Result.fail("请选择有效的图片文件");
            }
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename, category);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    @GetMapping("/comment/delete")
    public Result deleteCommentImg(@RequestParam("name") String filename) {
        if (StrUtil.isBlank(filename) || !filename.startsWith("/comments/") || filename.contains("..")) {
            return Result.fail("错误的文件名称");
        }
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename, String category) {
        if (StrUtil.isBlank(originalFilename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase();
        if (!"jpg".equals(suffix) && !"jpeg".equals(suffix) && !"png".equals(suffix)
                && !"webp".equals(suffix) && !"gif".equals(suffix)) {
            throw new IllegalArgumentException("不支持的图片格式");
        }
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/{}/{}/{}", category, d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/{}/{}/{}/{}.{}", category, d1, d2, name, suffix);
    }
}
