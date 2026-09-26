package legalcase.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据存储层。
 *
 * 数据文件：data/cases.json（JSON 数组，原子写盘：先写临时文件再 rename）。
 * 一个案例为一个聚合根，讲解章节 chapters 与课堂备注 notes 内嵌其中：
 *
 * {
 *   "id": "c001",
 *   "title": "...", "caseNumber": "...", "court": "...",
 *   "summary": "...", "facts": "...",
 *   "status": "draft" | "published",
 *   "chapters": [ {"id","heading","body","order"} ],
 *   "notes":    [ {"id","content","createdAt"} ],
 *   "createdAt": "...", "updatedAt": "..."
 * }
 *
 * 权限约定：学生只可见 status=published 的案例；课堂备注 notes 仅供教师，
 * 学生接口返回前一律剥离。
 */
public class Store {

    public static final String DRAFT = "draft";
    public static final String PUBLISHED = "published";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path dataFile;
    private final Object lock = new Object();
    private List<Map<String, Object>> cases;

    public Store(Path dataDir) {
        this.dataFile = dataDir.resolve("cases.json");
    }

    // ---------- 初始化 ----------

    @SuppressWarnings("unchecked")
    public void init() throws IOException {
        Files.createDirectories(dataFile.getParent());
        if (Files.exists(dataFile)) {
            String text = Files.readString(dataFile, StandardCharsets.UTF_8);
            if (!text.isBlank()) {
                Object parsed = Json.parse(text);
                if (!(parsed instanceof List)) {
                    throw new IOException("数据文件 " + dataFile + " 顶层结构应为数组");
                }
                cases = (List<Map<String, Object>>) parsed;
            } else {
                cases = new ArrayList<>();
            }
        } else {
            cases = SeedData.defaultCases();
            persist();
        }
    }

    private void persist() {
        try {
            Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            Files.writeString(tmp, Json.pretty(cases), StandardCharsets.UTF_8);
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("写入数据文件失败: " + dataFile, e);
        }
    }

    // ---------- 查询 ----------

    /** 列表：teacher 见全部，student 只见已发布（且剔除 notes）。 */
    public List<Map<String, Object>> list(String role) {
        synchronized (lock) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> c : cases) {
                if (isStudent(role) && !PUBLISHED.equals(c.get("status"))) continue;
                out.add(view(c, role));
            }
            return out;
        }
    }

    /** 详情。学生访问草稿抛 404 语义异常。 */
    public Map<String, Object> get(String role, String id) {
        synchronized (lock) {
            Map<String, Object> c = findOrThrow(id);
            if (isStudent(role) && !PUBLISHED.equals(c.get("status"))) {
                throw new NotFoundException("案例不存在或未发布");
            }
            return view(c, role);
        }
    }

    /**
     * 教师保存（聚合整体替换标题/摘要/事实/章节/备注，status 不由本方法修改）。
     * 草稿与已发布案例都可调用：已发布案例保存后内容立即更新（仍保持已发布）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> save(String id, Map<String, Object> body) {
        synchronized (lock) {
            Map<String, Object> c = findOrThrow(id);
            applyEditableFields(c, body);
            touch(c);
            persist();
            return view(c, "teacher");
        }
    }

    /** 新建案例：一律以草稿状态落库。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> create(Map<String, Object> body) {
        synchronized (lock) {
            String now = now();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", nextId());
            c.put("status", DRAFT);
            c.put("chapters", new ArrayList<Map<String, Object>>());
            c.put("notes", new ArrayList<Map<String, Object>>());
            c.put("createdAt", now);
            c.put("updatedAt", now);
            applyEditableFields(c, body);
            cases.add(c);
            persist();
            return view(c, "teacher");
        }
    }

    public Map<String, Object> setStatus(String id, String status) {
        synchronized (lock) {
            if (!DRAFT.equals(status) && !PUBLISHED.equals(status)) {
                throw new BadRequestException("status 只能为 draft 或 published");
            }
            Map<String, Object> c = findOrThrow(id);
            c.put("status", status);
            touch(c);
            persist();
            return view(c, "teacher");
        }
    }

    public void delete(String id) {
        synchronized (lock) {
            int idx = indexOf(id);
            if (idx < 0) throw new NotFoundException("案例不存在: " + id);
            cases.remove(idx);
            persist();
        }
    }

    /** 导出 Markdown 讲义（仅教师）。 */
    public String exportMarkdown(String id) {
        synchronized (lock) {
            Map<String, Object> c = findOrThrow(id);
            return buildMarkdown(c);
        }
    }

    // ---------- 内部方法 ----------

    @SuppressWarnings("unchecked")
    private void applyEditableFields(Map<String, Object> c, Map<String, Object> body) {
        c.put("title", str(body.get("title")));
        c.put("caseNumber", str(body.get("caseNumber")));
        c.put("court", str(body.get("court")));
        c.put("summary", str(body.get("summary")));
        c.put("facts", str(body.get("facts")));

        List<Object> rawChapters = asList(body.get("chapters"));
        List<Map<String, Object>> chapters = new ArrayList<>();
        int order = 1;
        for (Object o : rawChapters) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> ch = new LinkedHashMap<>();
            String chId = str(m.get("id"));
            ch.put("id", chId.isBlank() ? nextSubId("ch") : chId);
            ch.put("heading", str(m.get("heading")));
            ch.put("body", str(m.get("body")));
            ch.put("order", order++);
            chapters.add(ch);
        }
        c.put("chapters", chapters);

        List<Object> rawNotes = asList(body.get("notes"));
        List<Map<String, Object>> notes = new ArrayList<>();
        for (Object o : rawNotes) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String content = str(m.get("content"));
            if (content.isBlank()) continue;
            Map<String, Object> n = new LinkedHashMap<>();
            String nId = str(m.get("id"));
            n.put("id", nId.isBlank() ? nextSubId("n") : nId);
            n.put("content", content);
            String createdAt = str(m.get("createdAt"));
            n.put("createdAt", createdAt.isBlank() ? now() : createdAt);
            notes.add(n);
        }
        c.put("notes", notes);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> view(Map<String, Object> c, String role) {
        // 深拷贝，避免调用方改动内部结构
        Map<String, Object> copy = (Map<String, Object>) Json.parse(Json.stringify(c));
        if (isStudent(role)) {
            copy.remove("notes"); // 课堂备注不对学生开放
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private String buildMarkdown(Map<String, Object> c) {
        StringBuilder sb = new StringBuilder();
        String statusZh = PUBLISHED.equals(c.get("status")) ? "已发布" : "草稿";
        sb.append("# ").append(str(c.get("title"))).append("\n\n");
        sb.append("> 法律案例讲解稿（").append(statusZh).append("）  \n");
        sb.append("> 导出时间：").append(now()).append("\n\n");
        if (!str(c.get("caseNumber")).isBlank() || !str(c.get("court")).isBlank()) {
            sb.append("- 案号：").append(blank(str(c.get("caseNumber")), "—"))
              .append("\n- 审理法院：").append(blank(str(c.get("court")), "—")).append("\n\n");
        }
        sb.append("## 一、案情概要\n\n").append(orPlaceholder(str(c.get("summary")))).append("\n\n");
        sb.append("## 二、基本事实\n\n").append(orPlaceholder(str(c.get("facts")))).append("\n\n");

        List<Object> chapters = asList(c.get("chapters"));
        sb.append("## 三、讲解章节\n\n");
        if (chapters.isEmpty()) {
            sb.append("（暂未编写）\n\n");
        } else {
            int idx = 1;
            for (Object o : chapters) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String heading = str(m.get("heading"));
                sb.append("### ").append(idx++).append(". ")
                  .append(heading.isBlank() ? "（未命名章节）" : heading).append("\n\n")
                  .append(orPlaceholder(str(m.get("body")))).append("\n\n");
            }
        }

        sb.append("## 四、课堂备注（教师用，不随学生端展示）\n\n");
        List<Object> notes = asList(c.get("notes"));
        if (notes.isEmpty()) {
            sb.append("（无）\n");
        } else {
            int idx = 1;
            for (Object o : notes) {
                if (!(o instanceof Map<?, ?> m)) continue;
                sb.append(idx++).append(". ").append(str(m.get("content")))
                  .append("  _（").append(str(m.get("createdAt"))).append("）_\n");
            }
        }
        return sb.toString();
    }

    private Map<String, Object> findOrThrow(String id) {
        for (Map<String, Object> c : cases) {
            if (id.equals(c.get("id"))) return c;
        }
        throw new NotFoundException("案例不存在: " + id);
    }

    private int indexOf(String id) {
        for (int i = 0; i < cases.size(); i++) {
            if (id.equals(cases.get(i).get("id"))) return i;
        }
        return -1;
    }

    private String nextId() {
        int max = 0;
        for (Map<String, Object> c : cases) {
            String id = str(c.get("id"));
            if (id.startsWith("c")) {
                try { max = Math.max(max, Integer.parseInt(id.substring(1))); }
                catch (NumberFormatException ignored) {}
            }
        }
        return String.format("c%03d", max + 1);
    }

    private String nextSubId(String prefix) {
        return prefix + System.currentTimeMillis() + (int) (Math.random() * 1000);
    }

    private static void touch(Map<String, Object> c) {
        c.put("updatedAt", now());
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    private static boolean isStudent(String role) {
        return !"teacher".equals(role); // 未识别角色按学生（最小权限）处理
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private static String blank(String s, String fallback) { return s.isBlank() ? fallback : s; }

    private static String orPlaceholder(String s) { return s.isBlank() ? "（暂无内容）" : s; }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        if (o instanceof List<?> l) return (List<Object>) l;
        return Collections.emptyList();
    }

    // ---------- 业务异常 ----------

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String msg) { super(msg); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String msg) { super(msg); }
    }
}
