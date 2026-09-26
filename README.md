# 法律案例讲解稿系统

面向法学课堂的案例讲解管理系统：**教师**维护案例、讲解章节、课堂备注和发布状态；**学生**只能阅读已发布内容；教师可保存草稿、逐章发布、导出 Markdown 讲义。

技术栈：纯 Java（JDK 内置 `com.sun.net.httpserver`，**无任何第三方依赖，无需 Maven/Gradle**）+ 原生 HTML/CSS/JavaScript。Java 服务与网页工程**分目录**存放，服务同时托管网页，也支持跨端口联调。

---

## 一、目录结构

```
.
├── README.md                 # 本文档（运行、联调、数据文件位置说明）
├── server/                   # —— Java 服务工程 ——
│   ├── run.sh                # 一键编译 + 启动脚本
│   ├── src/com/lawcase/
│   │   ├── Main.java         # 入口：端口/数据/网页目录/口令配置，启动 HTTP 服务
│   │   ├── Api.java          # 全部 REST 接口与业务逻辑
│   │   ├── StaticFiles.java  # 托管 web/ 静态网页
│   │   ├── Store.java        # JSON 文件存储（原子写入 + 同步锁）
│   │   ├── Seed.java         # 首次启动的示例数据
│   │   ├── Json.java         # 极简 JSON 解析/序列化
│   │   └── ApiError.java
│   ├── out/                  # 编译产物（自动生成，已在 .gitignore）
│   └── data/
│       └── cases.json        # ★ 数据文件（首次启动自动生成，见第四节）
└── web/                      # —— 网页工程（纯静态） ——
    ├── index.html            # 学生端：已发布案例列表 + 详情阅读
    ├── teacher.html          # 教师端：登录、编辑、发布、导出
    └── assets/
        ├── app.css
        ├── api.js            # 接口封装（BASE、教师口令、下载讲义）
        ├── student.js
        └── teacher.js
```

---

## 二、快速开始

要求：**JDK 11 或以上**（推荐 17），仅需 `javac` / `java`，不需要联网下载依赖。

```bash
cd server
./run.sh                    # 等价于下面两条命令：
# javac -encoding UTF-8 -d out $(find src -name '*.java')
# java -cp out com.lawcase.Main
```

Windows PowerShell：

```powershell
cd server
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out com.lawcase.Main
```

启动后访问：

| 入口 | 地址 |
| --- | --- |
| 学生端（只读） | <http://localhost:8080/> |
| 教师端（管理） | <http://localhost:8080/teacher.html> |
| 健康检查 | <http://localhost:8080/api/health> |

教师端默认口令：**`teacher123`**（登录后保存在浏览器 localStorage，退出即清除）。

首次启动会自动生成示例数据：1 个「已发布」合同纠纷案例（含 2 个已发布章节 + 1 个草稿章节 + 1 条课堂备注）和 1 个「草稿」侵权案例，便于立即体验发布/下架差异。

### 配置项（环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `8080` | 监听端口 |
| `DATA_FILE` | `data/cases.json` | 数据文件路径（相对于启动时的工作目录） |
| `WEB_DIR` | `../web` | 网页静态目录；不存在时回退尝试 `./web` |
| `TEACHER_TOKEN` | `teacher123` | 教师接口口令 |

示例：

```bash
PORT=9000 TEACHER_TOKEN=mySecret DATA_FILE=/var/lib/lawcase/cases.json \
  java -cp server/out com.lawcase.Main
```

---

## 三、角色与功能

| 能力 | 学生端 `/` | 教师端 `/teacher.html` |
| --- | :---: | :---: |
| 浏览案例列表（仅已发布） | ✅ | ✅（另可见草稿及统计） |
| 阅读讲解章节（仅已发布章节） | ✅ | ✅ |
| 查看课堂备注 | ❌ 永不返回 | ✅ |
| 新建 / 编辑案例、保存草稿 | ❌ | ✅ |
| 章节级草稿 / 发布、下架 | ❌ | ✅ |
| 案例级发布 / 下架 | ❌ | ✅ |
| 课堂备注增删改 | ❌ | ✅ |
| 导出 Markdown 讲义（可含备注） | ❌ | ✅ |
| 打印 / 另存 PDF | ✅ | ✅（用浏览器打印） |

**可见性规则（重要）**

- 案例 `status = draft` 时，学生端列表与详情均不可见；
- 案例 `published` 后，学生端也**只看到其中 `published` 的章节**，草稿章节自动隐藏；
- `notes`（课堂备注）只在 `/api/teacher/*` 接口中出现，学生接口在服务端即剔除该字段。

---

## 四、数据文件位置与格式 ★

- **位置：`server/data/cases.json`**（相对 `server/` 目录启动时）。
- 首次启动若文件不存在，自动创建并写入 `Seed.java` 中的示例数据。
- 可用环境变量 `DATA_FILE` 指定其他绝对路径，便于备份或挂载持久卷。
- 每次保存采用「写临时文件 + 原子替换」，避免写坏；所有读写均加同步锁。
- **备份只需复制 `cases.json` 一个文件**；服务运行中也可直接复制。
- JSON 顶层为 `{ "cases": [ ... ] }`，每个案例对象结构：

```json
{
  "id": "c001",
  "title": "张某诉某科技公司买卖合同纠纷案",
  "caseNo": "（2025）京 0105 民初 1234 号",
  "court": "北京市朝阳区人民法院",
  "date": "2025-03-12",
  "category": "合同纠纷",
  "summary": "案情摘要……",
  "facts": "案件事实……",
  "status": "published",
  "createdAt": "2025-09-01T09:00:00Z",
  "updatedAt": "2025-09-20T14:30:00Z",
  "chapters": [
    {
      "id": "ch001", "order": 1,
      "title": "一、案件事实梳理",
      "content": "……",
      "status": "published",
      "createdAt": "...", "updatedAt": "..."
    }
  ],
  "notes": [
    { "id": "n001", "content": "课堂提问安排……", "createdAt": "...", "updatedAt": "..." }
  ]
}
```

字段约定：`status` 仅取 `draft` / `published`；章节 `order` 为整数，决定学生端与讲义中的顺序。

---

## 五、联调说明 ★

### 方式 A：同源（推荐，零配置）

Java 服务默认直接托管 `../web` 目录，前端 `web/assets/api.js` 中 `BASE = ''` 表示与页面同源：

- 学生端 <http://localhost:8080/> → 服务读取 `web/index.html`
- 教师端 <http://localhost:8080/teacher.html>
- 接口 <http://localhost:8080/api/...>

改完前端文件**刷新浏览器即可**，无需重启 Java 服务（静态文件每次实时读取）。

### 方式 B：前后端分端口开发

前端可用任意静态服务器独立运行，例如：

```bash
cd web
python3 -m http.server 5173        # 或 VSCode Live Server
```

然后把 `web/assets/api.js` 顶部改为：

```js
BASE: 'http://localhost:8080',
```

后端已为 `/api/*` 开启 **CORS**（`Access-Control-Allow-Origin: *`，并响应 OPTIONS 预检），
允许 `Content-Type` 与 `X-Teacher-Token` 头，跨端口可直接联调。

### 教师鉴权方式

所有 `/api/teacher/**` 接口必须带请求头：

```
X-Teacher-Token: teacher123
```

学生接口无需任何头。curl 联调示例：

```bash
curl -s http://localhost:8080/api/health
curl -s http://localhost:8080/api/published/cases
curl -s http://localhost:8080/api/teacher/cases -H "X-Teacher-Token: teacher123"
```

### REST 接口一览

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/health` | 否 | 健康检查 |
| GET | `/api/published/cases` | 否 | 学生：已发布案例列表 |
| GET | `/api/published/cases/{id}` | 否 | 学生：案例详情（仅已发布章节，无备注） |
| GET | `/api/teacher/cases` | 是 | 教师：全部案例及统计 |
| POST | `/api/teacher/cases` | 是 | 新建案例（默认草稿） |
| GET | `/api/teacher/cases/{id}` | 是 | 案例完整详情 |
| PUT | `/api/teacher/cases/{id}` | 是 | 更新基本信息（可带 `status`） |
| DELETE | `/api/teacher/cases/{id}` | 是 | 删除案例 |
| POST | `/api/teacher/cases/{id}/publish` | 是 | 发布案例 |
| POST | `/api/teacher/cases/{id}/unpublish` | 是 | 下架案例 |
| POST | `/api/teacher/cases/{id}/chapters` | 是 | 新增章节（默认草稿） |
| PUT | `/api/teacher/chapters/{id}` | 是 | 更新章节（标题/内容/顺序/状态） |
| DELETE | `/api/teacher/chapters/{id}` | 是 | 删除章节 |
| POST | `/api/teacher/chapters/{id}/publish` · `/unpublish` | 是 | 章节发布 / 下架 |
| POST | `/api/teacher/cases/{id}/notes` | 是 | 新增课堂备注 |
| PUT | `/api/teacher/notes/{id}` | 是 | 更新备注 |
| DELETE | `/api/teacher/notes/{id}` | 是 | 删除备注 |
| GET | `/api/teacher/cases/{id}/export?includeNotes=0|1` | 是 | 下载 Markdown 讲义 |

错误响应统一为 `{"error": "原因"}`，HTTP 状态码：400 参数错误 / 401 口令错误 / 404 不存在 / 405 方法不允许 / 500 服务错误。

### 导出讲义

- 教师端点击「导出 Markdown 讲义」会先保存当前编辑内容，再通过接口下载 `讲义-案例名.md`；
- 默认**不含课堂备注**；勾选「讲义附带课堂备注」后追加「五、课堂备注（仅教师可见）」；
- 草稿章节会以「（草稿）」标注，方便教师自查；学生端另有「打印 / 另存 PDF 讲义」按钮（浏览器打印，自动隐藏操作按钮）。

---

## 六、常见问题

1. **端口被占用**：`PORT=9090 ./run.sh`。
2. **忘记教师口令**：启动时设置 `TEACHER_TOKEN=新口令` 即可（口令不入库，只在服务端环境变量中）。
3. **想重置示例数据**：停止服务后删除 `server/data/cases.json`，再次启动即重新生成。
4. **Java 版本**：`java -version` 低于 11 时请升级 JDK；开发/测试版本为 Temurin JDK 17。
