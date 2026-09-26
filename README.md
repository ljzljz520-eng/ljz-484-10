# 法律案例讲解稿系统

面向法学课堂的案例讲解稿管理系统：

- **教师端**：维护案例、讲解章节、课堂备注；可保存草稿、发布/撤回、删除案例，并将讲义导出为 Markdown 文件。
- **学生端**：只能阅读**已发布**案例的基本信息与讲解章节；草稿不可见，**课堂备注在任何情况下都不对学生展示**。
- 后端为零依赖 Java 服务（JDK 内置 HTTP Server + 自研极简 JSON 解析，数据落本地 JSON 文件）；前端为纯静态网页工程。两个工程**分目录**存放。

## 一、目录结构与联调入口

```
.
├── backend/                 # Java 服务工程
│   ├── src/main/java/legalcase/
│   │   ├── Main.java            # 启动入口（HTTP 服务、路由挂载）
│   │   ├── store/
│   │   │   ├── Store.java       # 领域逻辑、权限过滤、原子写盘、Markdown 导出
│   │   │   ├── SeedData.java    # 首次启动的种子案例
│   │   │   └── Json.java        # 零依赖 JSON 解析/序列化
│   │   ├── api/ApiHandler.java  # /api 接口（角色鉴权、CORS）
│   │   └── web/StaticHandler.java  # 可选：托管 ../frontend 静态页面
│   ├── data/
│   │   └── cases.json        # ★ 数据文件位置（首次启动自动生成，见第三节）
│   ├── build.sh              # 编译脚本（只需 JDK，无需 Maven/Gradle）
│   └── run.sh                # 启动脚本
├── frontend/                # 网页工程（纯静态，可独立用任意静态服务器打开）
│   ├── index.html
│   ├── css/style.css
│   └── js/
│       ├── api.js            # fetch 封装，X-Role 请求头区分角色
│       └── app.js            # 学生阅读端 + 教师工作台
└── README.md
```

## 二、快速开始（联调步骤）

环境要求：JDK 11 或以上（在 JDK 17 上验证通过）。如系统未安装 JDK，
Linux 无 root 权限时可下载 Temurin 免安装包解压后设置 `JAVA_HOME`。

```bash
# 1) 启动 Java 服务（默认端口 8080，数据目录 backend/data，前端目录 ../frontend）
cd backend
./build.sh                 # 首次编译，产物在 backend/out/
./run.sh                   # 也可：./run.sh 8080 ./data ./../frontend
```

启动后：

- **网页入口（联调推荐）**：浏览器直接打开 <http://localhost:8080/> —— Java 服务会同时托管 `../frontend` 下的静态页面，前后端同源，无需额外配置。
- **接口基址**：<http://localhost:8080/api>
- 前端页面右上角切换「学生阅读端 / 教师工作台」。

### 前后端分离联调（可选）

接口已开启 CORS（`Access-Control-Allow-Origin: *`），前端也可以独立启动：

```bash
cd frontend
python3 -m http.server 5500        # 或 npx serve .
# 浏览器打开 http://localhost:5500，接口仍请求 http://当前host:8080/api
```

> `frontend/js/api.js` 中 `BASE='/api'` 使用相对路径。分离联调时若前端与 Java 服务不同源，
> 可将其改为 `http://localhost:8080/api`（Java 端 CORS 已放开，无需额外处理）。

`run.sh` 支持三个可选参数：`./run.sh [端口] [数据目录] [前端目录]`，
例如 `./run.sh 9090 /data/legalcase /opt/legalcase-frontend`。

## 三、数据文件位置

- **唯一数据文件：`backend/data/cases.json`**（JSON 数组，一个案例一个对象）。
- 首次启动若该文件不存在，会自动写入 3 条种子案例（2 篇已发布 + 1 篇草稿），用于演示与联调。
- 每次保存都会**原子写盘**（先写 `cases.json.tmp` 再 rename），避免写坏数据；可直接备份/手工编辑该文件后重启服务。
- 想重置演示数据：停止服务后删除 `backend/data/cases.json`，再次启动即可重新生成。

数据结构示例：

```json
{
  "id": "c001",
  "title": "张某诉某科技公司劳动争议案",
  "caseNumber": "（2024）京0105民初12345号",
  "court": "北京市朝阳区人民法院",
  "summary": "案情概要……",
  "facts": "基本事实……",
  "status": "published",
  "chapters": [ { "id": "ch101", "heading": "争议焦点梳理", "body": "……", "order": 1 } ],
  "notes":    [ { "id": "n201", "content": "课堂备注（仅教师可见）", "createdAt": "2026-09-10 09:20:00" } ],
  "createdAt": "2026-09-08 10:00:00",
  "updatedAt": "2026-09-12 14:05:00"
}
```

## 四、接口说明

角色通过请求头 **`X-Role: teacher`** 标识教师；不传或传其他值按**学生（最小权限）**处理。
这是教学演示用的轻量身份约定，不构成真实鉴权。

| 方法 | 路径 | 角色 | 说明 |
|------|------|------|------|
| GET | `/api/cases` | 全部 | 学生只返回 `published` 案例且**剥离 notes**；教师返回全部 |
| POST | `/api/cases` | 教师 | 新建案例，**强制以草稿状态**落库 |
| GET | `/api/cases/{id}` | 全部 | 学生访问草稿返回 404；学生响应中无 notes |
| PUT | `/api/cases/{id}` | 教师 | 保存标题/概要/事实/章节/备注（草稿与已发布均可保存；不改变 status） |
| DELETE | `/api/cases/{id}` | 教师 | 删除案例 |
| POST | `/api/cases/{id}/status` | 教师 | 发布/撤回，body：`{"status":"published"}` 或 `{"status":"draft"}` |
| GET | `/api/cases/{id}/export` | 教师 | 导出 Markdown 讲义（含课堂备注章节），返回下载文件 |

状态码约定：200/201 成功；400 请求体或参数错误；403 学生越权写操作；404 案例不存在/学生访问未发布案例。

curl 速览：

```bash
# 学生视角（无 X-Role）
curl http://localhost:8080/api/cases

# 教师视角
curl -H "X-Role: teacher" http://localhost:8080/api/cases
# 发布
curl -X POST -H "X-Role: teacher" -H "Content-Type: application/json" \
     -d '{"status":"published"}' http://localhost:8080/api/cases/c002/status
# 导出讲义
curl -H "X-Role: teacher" -OJ http://localhost:8080/api/cases/c001/export
```

## 五、使用流程提示

1. 教师「新建案例」→ 案例自动是**草稿**，学生端不可见。
2. 教师填写基本信息、讲解章节、课堂备注，随时点「保存草稿/修改」。
3. 点「发布给学生」会先保存当前内容再切换为已发布，学生端立即可读；
   已发布案例也可继续修改并保存（学生端即时看到更新），或「撤回为草稿」重新隐藏。
4. 「导出讲义」生成 Markdown：含案情概要、基本事实、讲解章节，以及仅教师使用的「课堂备注」章节，可印发或二次排版。

## 六、技术说明

- 后端仅用 JDK（`com.sun.net.httpserver.HttpServer`），JSON 解析为 `Json.java` 内的自研实现，无任何第三方依赖，离线环境可编译运行。
- 全部写操作在进程内串行化（synchronized），适合课堂/教研室单机使用；如需多人并发部署，可将 `Store` 替换为数据库实现。
