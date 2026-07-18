# CityHub Vue 桌面端

基于 Vue 3、Vue Router、Axios 与 Vite 的桌面端单页应用。旧版静态页面仍保留在 `front/html/hmdp`。

## 本地开发

确保后端运行在 `http://localhost:8081`，然后执行：

```bash
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 代理到后端，把 `/imgs` 代理到旧前端静态资源服务。

## 生产构建

```bash
npm run build
```

构建产物位于 `dist`，项目使用根目录下的 `nginx.exe` 和 `nginx.conf` 独立部署。`public/imgs` 中的旧版图片会在构建时自动复制到 `dist/imgs`。

```powershell
npm run build
.\nginx.exe -t -p .\ -c nginx.conf
.\nginx.exe -p .\ -c nginx.conf
```

## 已迁移页面

- 首页与商家分类
- 商家列表、商家详情、优惠券秒杀
- 验证码登录、密码登录
- 个人中心、其他用户主页
- 探店笔记详情与发布页面

普通优惠券购买仍缺少后端接口，页面会给出明确提示。
