import http from './http'

/**
 * 对接 BlogController.saveBlog(@RequestBody Blog blog)
 * POST /api/blog（Nginx 会把 /api 前缀去掉后转发给后端 /blog）
 */
export const publishBlog = blog => http.post('/blog', blog)

