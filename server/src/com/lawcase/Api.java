package com.lawcase;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * 接口路由与业务处理。
 *
 * 学生端（无需口令，只读）：
 *   GET /api/health
 *   GET /api/published/cases
 *   GET /api/published/cases/{id}
 *
 * 教师端（请求头 X-Teacher-Token）：
 *   GET    /api/teacher/cases                       全部案例（含草稿、备注）
 *   POST   /api/teacher/cases                       新建案例（默认 draft）
 *   GET    /api/teacher/cases/{id}                  案例详情
 *   PUT    /api/teacher/cases/{id}                  更新基本信息（可带 status）
 *   DELETE /api/teacher/cases/{id}                  删除案例
 *   POST   /api/teacher/cases/{id}/publish          发布案例
 *   POST   /api/teacher/cases/{id}/unpublish        下架案例
 *   POST   /api/teacher/cases/{id}/chapters         新增讲解章节
 *   GET    /api/teacher/cases/{id}/export?includeNotes=1   导出 Markdown 讲义
 *   POST   /api/teacher/cases/{id}/notes            新增课堂备注
 *   PUT    /api/teacher/chapters/{id}               更新章节
 *   DELETE /api/teacher/chapters/{id}               删除章节
 *   POST   /api/teacher/chapters/{id}/publish       发布章节
 *   POST   /api/teacher/chapters/{id}/unpublish     下架章节
 *   PUT    /api/teacher/notes/{id}                  更新备注
 *   DELETE /api/teacher/notes/{id}                  删除备注
 */
public class Api implements HttpHandler {
    private static final String DRAFT = "draft";
    private static final String PUBLISHED = "published";

    private final Store store;
    private final String teacherToken;

    public Api(Store store, String teacherToken) {
        this.store = store;
        this.teacherToken = teacherToken;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        try {
            route(ex);
        } catch (ApiError e) {
            sendJson(ex, e.status, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("error", "服务器内部错误: " + e.getMessage()));
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = ex.getRequestURI().getPath();
        if (!path.startsWith("/api/")) throw new ApiError(404, "接口不存在: " + path);
        String[] seg = path.substring("/api/".length()).split("/");

        if (seg.length == 1 && "health".equals(seg[0]) && "GET".equals(method)) {
            sendJson(ex, 200, Map.of("status", "ok", "time", now()));
            return;
        }

        if ("published".equals(seg[0])) {
            handlePublished(ex, method, seg);
            return;
        }

        if ("teacher".equals(seg[0])) {
            requireTeacher(ex);
            handleTeacher(ex, method, seg);
            return;
        }

        throw new ApiError(404, "接口不存在: " + path);
    }

    // ---------------- 学生端 ----------------

    private void handlePublished(HttpExchange ex, String method, String[] seg) throws IOException {
        if ("GET".equals(method) && seg.length == 2 && "cases".equals(seg[1])) {
            synchronized (store) {
                List<Object> list = new ArrayList<>();
                for (Map<String, Object> c : store.cases()) {
                    if (PUBLISHED.equals(c.get("status"))) list.add(caseSummary(c));
                }
                sendJson(ex, 200, Map.of("cases", list));
            }
            return;
        }
        if ("GET".equals(method) && seg.length == 3 && "cases".equals(seg[1])) {
            synchronized (store) {
                Map<String, Object> c = store.findCase(seg[2]);
                if (c == null || !PUBLISHED.equals(c.get("status"))) {
                    throw new ApiError(404, "案例不存在或尚未发布");
                }
                sendJson(ex, 200, Map.of("case", publishedView(c)));
            }
            return;
        }
        throw new ApiError(404, "接口不存在");
    }

    /** 学生视图：绝不包含 notes，章节仅保留已发布且按 order 排序。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> publishedView(Map<String, Object> c) {
        Map<String, Object> v = publicFields(c);
        List<Object> chapters = new ArrayList<>();
        for (Map<String, Object> ch : sortedChapters(c)) {
            if (PUBLISHED.equals(ch.get("status"))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", ch.get("id"));
                m.put("order", ch.get("order"));
                m.put("title", ch.get("title"));
                m.put("content", ch.get("content"));
                chapters.add(m);
            }
        }
        v.put("chapters", chapters);
        return v;
    }

    private Map<String, Object> publicFields(Map<String, Object> c) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : new String[]{"id", "title", "caseNo", "court", "date",
                "category", "summary", "facts", "status", "createdAt", "updatedAt"}) {
            m.put(k, c.getOrDefault(k, ""));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> caseSummary(Map<String, Object> c) {
        Map<String, Object> m = publicFields(c);
        m.remove("facts");
        long publishedChapters = sortedChapters(c).stream()
                .filter(ch -> PUBLISHED.equals(ch.get("status"))).count();
        m.put("publishedChapters", publishedChapters);
        return m;
    }

    // ---------------- 教师端 ----------------

    private void handleTeacher(HttpExchange ex, String method, String[] seg) throws IOException {
        if (seg.length < 2) throw new ApiError(404, "接口不存在");

        if ("cases".equals(seg[1])) {
            handleCases(ex, method, seg);
            return;
        }
        if ("chapters".equals(seg[1])) {
            if (seg.length == 3 && "PUT".equals(method)) { updateChapter(ex, seg[2]); return; }
            if (seg.length == 3 && "DELETE".equals(method)) { deleteChapter(ex, seg[2]); return; }
            if (seg.length == 4 && "POST".equals(method)
                    && ("publish".equals(seg[3]) || "unpublish".equals(seg[3]))) {
                setChapterStatus(ex, seg[2], seg[3]);
                return;
            }
        }
        if ("notes".equals(seg[1])) {
            if (seg.length == 3 && "PUT".equals(method)) { updateNote(ex, seg[2]); return; }
            if (seg.length == 3 && "DELETE".equals(method)) { deleteNote(ex, seg[2]); return; }
        }
        throw new ApiError(404, "接口不存在");
    }

    private void handleCases(HttpExchange ex, String method, String[] seg) throws IOException {
        // /teacher/cases
        if (seg.length == 2) {
            if ("GET".equals(method)) {
                synchronized (store) {
                    List<Object> list = new ArrayList<>();
                    for (Map<String, Object> c : store.cases()) list.add(teacherSummary(c));
                    sendJson(ex, 200, Map.of("cases", list));
                }
            } else if ("POST".equals(method)) {
                createCase(ex);
            } else {
                throw new ApiError(405, "不支持的请求方法");
            }
            return;
        }

        String id = seg[2];
        // /teacher/cases/{id}
        if (seg.length == 3) {
            switch (method) {
                case "GET":
                    synchronized (store) {
                        Map<String, Object> c = mustFindCase(id);
                        sendJson(ex, 200, Map.of("case", c));
                    }
                    return;
                case "PUT": updateCase(ex, id); return;
                case "DELETE": deleteCase(ex, id); return;
                default: throw new ApiError(405, "不支持的请求方法");
            }
        }

        // /teacher/cases/{id}/xxx
        if (seg.length == 4) {
            switch (seg[3]) {
                case "publish":
                    if ("POST".equals(method)) { setCaseStatus(ex, id, PUBLISHED); return; }
                    break;
                case "unpublish":
                    if ("POST".equals(method)) { setCaseStatus(ex, id, DRAFT); return; }
                    break;
                case "chapters":
                    if ("POST".equals(method)) { addChapter(ex, id); return; }
                    break;
                case "notes":
                    if ("POST".equals(method)) { addNote(ex, id); return; }
                    break;
                case "export":
                    if ("GET".equals(method)) { exportHandout(ex, id); return; }
                    break;
            }
        }
        throw new ApiError(404, "接口不存在");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> teacherSummary(Map<String, Object> c) {
        Map<String, Object> m = publicFields(c);
        List<Map<String, Object>> chs = (List<Map<String, Object>>) c.get("chapters");
        List<Map<String, Object>> notes = (List<Map<String, Object>>) c.get("notes");
        m.put("chapterCount", chs == null ? 0 : chs.size());
        m.put("publishedChapters", chs == null ? 0
                : chs.stream().filter(ch -> PUBLISHED.equals(ch.get("status"))).count());
        m.put("noteCount", notes == null ? 0 : notes.size());
        return m;
    }

    private void createCase(HttpExchange ex) throws IOException {
        Map<String, Object> body = bodyJson(ex);
        String title = requireText(body, "title");
        Map<String, Object> c = store.newContainer();
        c.put("id", newId());
        c.put("title", title);
        c.put("caseNo", str(body, "caseNo"));
        c.put("court", str(body, "court"));
        c.put("date", str(body, "date"));
        c.put("category", str(body, "category"));
        c.put("summary", str(body, "summary"));
        c.put("facts", str(body, "facts"));
        c.put("status", DRAFT);
        c.put("createdAt", now());
        c.put("updatedAt", now());
        c.put("chapters", new ArrayList<>());
        c.put("notes", new ArrayList<>());
        synchronized (store) {
            store.cases().add(c);
            store.save();
        }
        sendJson(ex, 200, Map.of("case", c));
    }

    private void updateCase(HttpExchange ex, String id) throws IOException {
        Map<String, Object> body = bodyJson(ex);
        synchronized (store) {
            Map<String, Object> c = mustFindCase(id);
            if (body.containsKey("title")) {
                String title = str(body, "title");
                if (title.isBlank()) throw new ApiError(400, "案例名称不能为空");
                c.put("title", title);
            }
            for (String k : new String[]{"caseNo", "court", "date", "category", "summary", "facts"}) {
                if (body.containsKey(k)) c.put(k, str(body, k));
            }
            if (body.containsKey("status")) {
                String s = str(body, "status");
                if (!DRAFT.equals(s) && !PUBLISHED.equals(s)) throw new ApiError(400, "status 只能是 draft 或 published");
                c.put("status", s);
            }
            c.put("updatedAt", now());
            store.save();
            sendJson(ex, 200, Map.of("case", c));
        }
    }

    private void deleteCase(HttpExchange ex, String id) throws IOException {
        synchronized (store) {
            Map<String, Object> c = mustFindCase(id);
            store.cases().remove(c);
            store.save();
        }
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void setCaseStatus(HttpExchange ex, String id, String status) throws IOException {
        synchronized (store) {
            Map<String, Object> c = mustFindCase(id);
            c.put("status", status);
            c.put("updatedAt", now());
            store.save();
            sendJson(ex, 200, Map.of("case", c));
        }
    }

    @SuppressWarnings("unchecked")
    private void addChapter(HttpExchange ex, String caseId) throws IOException {
        Map<String, Object> body = bodyJson(ex);
        String title = requireText(body, "title");
        Map<String, Object> ch = new LinkedHashMap<>();
        ch.put("id", newId());
        ch.put("title", title);
        ch.put("content", str(body, "content"));
        ch.put("status", DRAFT);
        ch.put("createdAt", now());
        ch.put("updatedAt", now());

        synchronized (store) {
            Map<String, Object> c = mustFindCase(caseId);
            List<Map<String, Object>> chs = (List<Map<String, Object>>) c.get("chapters");
            long order;
            if (body.get("order") instanceof Number) {
                order = ((Number) body.get("order")).longValue();
            } else {
                order = chs.stream().mapToLong(x -> toLong(x.get("order"))).max().orElse(0) + 1;
            }
            ch.put("order", order);
            chs.add(ch);
            c.put("updatedAt", now());
            store.save();
        }
        sendJson(ex, 200, Map.of("chapter", ch));
    }

    @SuppressWarnings("unchecked")
    private void updateChapter(HttpExchange ex, String chapterId) throws IOException {
        Map<String, Object> body = bodyJson(ex);
        synchronized (store) {
            Map<String, Object> c = store.findChapter(chapterId);
            if (c == null) throw new ApiError(404, "章节不存在");
            Map<String, Object> ch = (Map<String, Object>)
                    ((List<?>) c.get("chapters")).stream()
                            .filter(x -> chapterId.equals(((Map<String, Object>) x).get("id")))
                            .findFirst().orElseThrow(() -> new ApiError(404, "章节不存在"));
            if (body.containsKey("title")) {
                String title = str(body, "title");
                if (title.isBlank()) throw new ApiError(400, "章节标题不能为空");
                ch.put("title", title);
            }
            if (body.containsKey("content")) ch.put("content", str(body, "content"));
            if (body.get("order") instanceof Number) ch.put("order", ((Number) body.get("order")).longValue());
            if (body.containsKey("status")) {
                String s = str(body, "status");
                if (!DRAFT.equals(s) && !PUBLISHED.equals(s)) throw new ApiError(400, "status 只能是 draft 或 published");
                ch.put("status", s);
            }
            ch.put("updatedAt", now());
            c.put("updatedAt", now());
            store.save();
            sendJson(ex, 200, Map.of("chapter", ch));
        }
    }

    @SuppressWarnings("unchecked")
    private void deleteChapter(HttpExchange ex, String chapterId) throws IOException {
        synchronized (store) {
            Map<String, Object> c = store.findChapter(chapterId);
            if (c == null) throw new ApiError(404, "章节不存在");
            List<Map<String, Object>> chs = (List<Map<String, Object>>) c.get("chapters");
            boolean removed = chs.removeIf(ch -> chapterId.equals(ch.get("id")));
            if (!removed) throw new ApiError(404, "章节不存在");
            c.put("updatedAt", now());
            store.save();
        }
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void setChapterStatus(HttpExchange ex, String chapterId, String action) throws IOException {
        String status = "publish".equals(action) ? PUBLISHED : DRAFT;
        synchronized (store) {
            Map<String, Object> c = store.findChapter(chapterId);
            if (c == null) throw new ApiError(404, "章节不存在");
            for (Map<String, Object> ch : sortedChapters(c)) {
                if (chapterId.equals(ch.get("id"))) {
                    ch.put("status", status);
                    ch.put("updatedAt", now());
                    c.put("updatedAt", now());
                    store.save();
                    sendJson(ex, 200, Map.of("chapter", ch));
                    return;
                }
            }
        }
        throw new ApiError(404, "章节不存在");
    }

    @SuppressWarnings("unchecked")
    private void addNote(HttpExchange ex, String caseId) throws IOException {
        Map<String, Object> body = bodyJson(ex);
        String content = requireText(body, "content");
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", newId());
        n.put("content", content);
        n.put("createdAt", now());
        n.put("updatedAt", now());
        synchronized (store) {
            Map<String, Object> c = mustFindCase(caseId);
            ((List<Map<String, Object>>) c.get("notes")).add(n);
            c.put("updatedAt", now());
            store.save();
        }
        sendJson(ex, 200, Map.of("note", n));
    }

    @SuppressWarnings("unchecked")
    private void updateNote(HttpExchange ex, String noteId) throws IOException {
        Map<String, Object> body = bodyJson(ex);
        String content = requireText(body, "content");
        synchronized (store) {
            Map<String, Object> c = store.findNote(noteId);
            if (c == null) throw new ApiError(404, "备注不存在");
            for (Map<String, Object> n : (List<Map<String, Object>>) c.get("notes")) {
                if (noteId.equals(n.get("id"))) {
                    n.put("content", content);
                    n.put("updatedAt", now());
                    c.put("updatedAt", now());
                    store.save();
                    sendJson(ex, 200, Map.of("note", n));
                    return;
                }
            }
        }
        throw new ApiError(404, "备注不存在");
    }

    @SuppressWarnings("unchecked")
    private void deleteNote(HttpExchange ex, String noteId) throws IOException {
        synchronized (store) {
            Map<String, Object> c = store.findNote(noteId);
            if (c == null) throw new ApiError(404, "备注不存在");
            List<Map<String, Object>> notes = (List<Map<String, Object>>) c.get("notes");
            boolean removed = notes.removeIf(n -> noteId.equals(n.get("id")));
            if (!removed) throw new ApiError(404, "备注不存在");
            c.put("updatedAt", now());
            store.save();
        }
        sendJson(ex, 200, Map.of("ok", true));
    }

    // ---------------- 导出讲义（Markdown） ----------------

    @SuppressWarnings("unchecked")
    private void exportHandout(HttpExchange ex, String id) throws IOException {
        boolean includeNotes = "1".equals(query(ex).getOrDefault("includeNotes", "0"))
                || "true".equalsIgnoreCase(query(ex).get("includeNotes"));
        String md;
        String safeName;
        synchronized (store) {
            Map<String, Object> c = mustFindCase(id);
            StringBuilder sb = new StringBuilder();
            sb.append("# 案例讲义：").append(str(c, "title")).append("\n\n");
            sb.append("> 导出时间：").append(now()).append("　|　讲义状态：")
              .append(PUBLISHED.equals(c.get("status")) ? "已发布" : "草稿").append("\n\n");
            sb.append("## 一、基本信息\n\n");
            sb.append("- 案例名称：").append(str(c, "title")).append('\n');
            sb.append("- 案　　号：").append(blank(str(c, "caseNo"))).append('\n');
            sb.append("- 审理法院：").append(blank(str(c, "court"))).append('\n');
            sb.append("- 裁判日期：").append(blank(str(c, "date"))).append('\n');
            sb.append("- 案例类别：").append(blank(str(c, "category"))).append("\n\n");
            sb.append("## 二、案情摘要\n\n").append(blank(str(c, "summary"))).append("\n\n");
            sb.append("## 三、案件事实\n\n").append(blank(str(c, "facts"))).append("\n\n");
            sb.append("## 四、讲解章节\n\n");
            int i = 1;
            for (Map<String, Object> ch : sortedChapters(c)) {
                sb.append("### ").append(i++).append(". ").append(str(ch, "title"));
                if (!PUBLISHED.equals(ch.get("status"))) sb.append("（草稿）");
                sb.append("\n\n").append(blank(str(ch, "content"))).append("\n\n");
            }
            if (includeNotes) {
                List<Map<String, Object>> notes = (List<Map<String, Object>>) c.get("notes");
                sb.append("## 五、课堂备注（仅教师可见，请勿随讲义分发）\n\n");
                if (notes == null || notes.isEmpty()) {
                    sb.append("（无）\n");
                } else {
                    for (Map<String, Object> n : notes) {
                        sb.append("- ").append(str(n, "content").replace("\n", "\n  ")).append('\n');
                    }
                }
            }
            md = sb.toString();
            safeName = "讲义-" + str(c, "title").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        }
        byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/markdown; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"handout.md\"; filename*=UTF-8''"
                        + java.net.URLEncoder.encode(safeName + ".md", StandardCharsets.UTF_8).replace("+", "%20"));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ---------------- 工具方法 ----------------

    private Map<String, Object> mustFindCase(String id) {
        Map<String, Object> c = store.findCase(id);
        if (c == null) throw new ApiError(404, "案例不存在: " + id);
        return c;
    }

    private void requireTeacher(HttpExchange ex) {
        String t = ex.getRequestHeaders().getFirst("X-Teacher-Token");
        if (t == null || !t.equals(teacherToken)) {
            throw new ApiError(401, "教师口令无效或缺失（请在请求头 X-Teacher-Token 中提供）");
        }
    }

    private void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Teacher-Token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyJson(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) return new LinkedHashMap<>();
        Object parsed;
        try {
            parsed = Json.parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ApiError(400, "请求体不是合法 JSON: " + e.getMessage());
        }
        if (!(parsed instanceof Map)) throw new ApiError(400, "请求体必须是 JSON 对象");
        return (Map<String, Object>) parsed;
    }

    private Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            try {
                String k = eq < 0 ? pair : pair.substring(0, eq);
                String v = eq < 0 ? "" : pair.substring(eq + 1);
                q.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
                      URLDecoder.decode(v, StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        return q;
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sortedChapters(Map<String, Object> c) {
        List<Map<String, Object>> chs = new ArrayList<>((List<Map<String, Object>>) c.get("chapters"));
        chs.sort((a, b) -> {
            int cmp = Long.compare(toLong(a.get("order")), toLong(b.get("order")));
            if (cmp != 0) return cmp;
            return String.valueOf(a.get("id")).compareTo(String.valueOf(b.get("id")));
        });
        return chs;
    }

    private static long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String requireText(Map<String, Object> m, String key) {
        String v = str(m, key).trim();
        if (v.isEmpty()) throw new ApiError(400, "字段不能为空: " + key);
        return v;
    }

    private static String blank(String s) { return s == null || s.isEmpty() ? "（无）" : s; }

    private static String now() { return Instant.now().toString(); }

    private static String newId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 10); }
}
