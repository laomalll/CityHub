-- 封装函数，发送http请求，并解析响应
local function read_http(path, params)
    local resp = ngx.location.capture(path,{
        method = ngx.HTTP_GET, -- get方式请求
        args = params,
    })
    -- 查询失败
    if not resp then
        -- 记录错误信息，返回404
        ngx.log(ngx.ERR, "http请求查询失败, path: ", path , ", args: ", args)
        ngx.exit(404)
    end
    -- 查询成功，返回响应体数据
    return resp.body
end
-- 将方法导出
local _M = {
    read_http = read_http
}
return _M