package com.lawcase;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** 托管 web/ 目录下的静态网页资源，带目录穿越防护。 */
public class StaticFiles implements HttpHandler {
    private final Path root;

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry(".html", "text/html; charset=utf-8"),
            Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".js", "text/javascript; charset=utf-8"),
            Map.entry(".json", "application/json; charset=utf-8"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".md", "text/markdown; charset=utf-8"),
            Map.entry(".txt", "text/plain; charset=utf-8")
    );

    public StaticFiles(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            sendText(ex, 405, "Method Not Allowed");
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        // 去掉前导 "/" 后相对 root 解析，并校验仍在 root 之内（防穿越）
        Path file = root.resolve(path.substring(1)).normalize();
        if (!file.startsWith(root) || Files.isDirectory(file) || !Files.isRegularFile(file)) {
            sendText(ex, 404, "404 Not Found");
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        String type = TYPES.getOrDefault(extension(file), "application/octet-stream");
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, "HEAD".equals(method) ? -1 : bytes.length);
        if ("GET".equals(method)) {
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } else {
            ex.close();
        }
    }

    private static String extension(Path p) {
        String n = p.getFileName().toString();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i).toLowerCase();
    }

    private void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
