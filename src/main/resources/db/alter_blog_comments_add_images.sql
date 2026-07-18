-- 已有 tb_blog_comments 表的增量升级脚本，只增加评论图片字段。
ALTER TABLE `tb_blog_comments`
    ADD COLUMN `images` varchar(1024) NULL DEFAULT NULL COMMENT '评论图片，多个路径以逗号分隔'
    AFTER `content`;
