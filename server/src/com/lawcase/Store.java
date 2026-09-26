package com.lawcase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据存储：以单个 JSON 文件作为数据库。
 * 所有读写都加 synchronized 锁，保存时先写临时文件再原子替换。
 *
 * 文件结构：
 * {
 *   "cases": [
 *     {
 *       "id", "title", "caseNo", "court", "date", "category",
 *       "summary", "facts", "status": "draft|published",
 *       "createdAt", "updatedAt",
 *       "chapters": [ {id,title,order,content,status,createdAt,updatedAt} ],
 *       "notes":    [ {id,content,createdAt,updatedAt} ]
 *     }
 *   ]
 * }
 */
public class Store {
    private final Path file;
    private Map<String, Object> data;

    public Store(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        if (Files.exists(file) && Files.size(file) > 0) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = (Map<String, Object>) Json.parse(text);
            data = parsed;
            if (data.get("cases") == null) data.put("cases", new java.util.ArrayList<>());
        } else {
            data = Seed.sample();
            save();
        }
    }

    public synchronized void save() throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.stringify(data), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @SuppressWarnings("unchecked")
    public synchronized List<Map<String, Object>> cases() {
        return (List<Map<String, Object>>) data.get("cases");
    }

    public synchronized Map<String, Object> findCase(String id) {
        for (Map<String, Object> c : cases()) {
            if (id.equals(c.get("id"))) return c;
        }
        return null;
    }

    /** 在所有案例中查找章节，返回所在案例；找不到返回 null。 */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> findChapter(String chapterId) {
        for (Map<String, Object> c : cases()) {
            for (Map<String, Object> ch : (List<Map<String, Object>>) c.get("chapters")) {
                if (chapterId.equals(ch.get("id"))) return c;
            }
        }
        return null;
    }

    /** 在所有案例中查找备注，返回所在案例；找不到返回 null。 */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> findNote(String noteId) {
        for (Map<String, Object> c : cases()) {
            for (Map<String, Object> n : (List<Map<String, Object>>) c.get("notes")) {
                if (noteId.equals(n.get("id"))) return c;
            }
        }
        return null;
    }

    public synchronized Map<String, Object> newContainer() {
        return new LinkedHashMap<>();
    }
}
