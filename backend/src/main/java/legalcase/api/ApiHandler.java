package legalcase.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import legalcase.store.Json;
import legalcase.store.Store;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api 下的 JSON 接口。
 * 角色通过请求头 X-Role 传入：teacher=教师；缺省/其他=学生（最小权限）。
 *
 *  GET    /api/cases               列表（学生只含已发布，且无 notes）
 *  POST   /api/cases               教师新建（落库为草稿）
 *  GET    /api/cases/{id}          详情（学生访问草稿返回 404）
 *  PUT    /api/cases/{id}          教师保存（可用于保存草稿/更新已发布内容）
 *  DELETE /api/cases/{id}          教师删除
 *  POST   /api/cases/{id}/status   教师发布/撤回  body: {"status":"published|draft"}
 *  GET    /api/cases/{id}/export   教师导出 Markdown 讲义
 */
public class ApiHandler implements HttpHandler {

    private final Store store;

    public ApiHandler(Store store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 204, null);
                return;
            }
            route(ex);
        } catch (Store.NotFoundException e) {
            sendError(ex, 404, e.getMessage());
        } catch (Store.ForbiddenException e) {
            sendError(ex, 403, e.getMessage());
        } catch (Store.BadRequestException e) {
            sendError(ex, 400, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, "请求数据格式有误：" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ex, 500, "服务器内部错误：" + e.getMessage());
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        String[] seg = segments(ex.getRequestURI().getRawPath()); // ["api","cases",id,...]
        String role = ex.getRequestHeaders().getFirst("X-Role");

        // /api/cases
        if (seg.length == 2 && "cases".equals(seg[1])) {
            switch (method) {
                case "GET" -> sendJson(ex, 200, store.list(role));
                case "POST" -> {
                    requireTeacher(role, ex);
                    Map<String, Object> body = readJsonBody(ex);
                    sendJson(ex, 201, store.create(body));
                }
                default -> sendError(ex, 405, "不支持的请求方法");
            }
            return;
        }

        // /api/cases/{id}[/status|/export]
        if (seg.length >= 3 && "cases".equals(seg[1])) {
            String id = decode(seg[2]);
            String action = seg.length == 4 ? seg[3] : "";

            if (seg.length == 3) {
                switch (method) {
                    case "GET" -> sendJson(ex, 200, store.get(role, id));
                    case "PUT" -> {
                        requireTeacher(role, ex);
                        sendJson(ex, 200, store.save(id, readJsonBody(ex)));
                    }
                    case "DELETE" -> {
                        requireTeacher(role, ex);
                        store.delete(id);
                        Map<String, Object> ok = new LinkedHashMap<>();
                        ok.put("ok", true);
                        sendJson(ex, 200, ok);
                    }
                    default -> sendError(ex, 405, "不支持的请求方法");
                }
                return;
            }

            if ("status".equals(action) && method.equals("POST")) {
                requireTeacher(role, ex);
                Map<String, Object> body = readJsonBody(ex);
                Object status = body.get("status");
                if (status == null) throw new Store.BadRequestException("缺少 status 字段");
                sendJson(ex, 200, store.setStatus(id, String.valueOf(status)));
                return;
            }

            if ("export".equals(action) && method.equals("GET")) {
                requireTeacher(role, ex);
                sendMarkdown(ex, id, store.exportMarkdown(id));
                return;
            }
        }

        sendError(ex, 404, "接口不存在");
    }

    // ---------- 鉴权 / 请求解析 ----------

    private void requireTeacher(String role, HttpExchange ex) {
        if (!"teacher".equals(role)) {
            throw new Store.ForbiddenException("当前为学生身份，仅可阅读已发布内容");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) return new LinkedHashMap<>();
        String text = new String(bytes, StandardCharsets.UTF_8);
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("请求体应为 JSON 对象");
        return (Map<String, Object>) parsed;
    }

    // ---------- 响应 ----------

    private void sendJson(HttpExchange ex, int code, Object payload) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] data = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private void sendMarkdown(HttpExchange ex, String id, String md) throws IOException {
        String filename = "讲义-" + id + "-" + System.currentTimeMillis() + ".md";
        ex.getResponseHeaders().set("Content-Type", "text/markdown; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename*=UTF-8''" + urlEncode(filename));
        byte[] data = md.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private void sendError(HttpExchange ex, int code, String msg) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", msg);
        sendJson(ex, code, err);
    }

    private void send(HttpExchange ex, int code, byte[] data) throws IOException {
        ex.sendResponseHeaders(code, data == null ? -1 : data.length);
        if (data != null) {
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        } else {
            ex.getResponseBody().close();
        }
    }

    private void addCors(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, X-Role");
        h.set("Access-Control-Max-Age", "86400");
    }

    private static String[] segments(String path) {
        String[] raw = path.split("/");
        List<String> out = new java.util.ArrayList<>();
        for (String s : raw) {
            if (!s.isBlank()) out.add(s);
        }
        return out.toArray(new String[0]);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
