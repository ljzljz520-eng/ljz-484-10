package legalcase.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 可选的静态资源托管：服务启动时若能定位到 ../../frontend 目录，
 * 访问 http://localhost:8080/ 即可直接打开网页，方便一体联调。
 * 前端独立用静态服务器打开亦可（接口已开启 CORS）。
 */
public class StaticHandler implements HttpHandler {

    private static final Map<String, String> MIME = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "application/javascript; charset=utf-8",
            ".json", "application/json; charset=utf-8",
            ".svg", "image/svg+xml",
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".ico", "image/x-icon"
    );

    private final Path root;

    public StaticHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.getResponseBody().close();
            return;
        }
        String raw = ex.getRequestURI().getPath();
        if (raw.equals("/")) raw = "/index.html";
        Path target = root.resolve(raw.startsWith("/") ? raw.substring(1) : raw).normalize();

        // 防目录穿越
        if (!target.startsWith(root) || Files.isDirectory(target) || !Files.exists(target)) {
            byte[] msg = "404 Not Found".getBytes();
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }

        byte[] data = Files.readAllBytes(target);
        String mime = MIME.entrySet().stream()
                .filter(e -> target.getFileName().toString().endsWith(e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse("application/octet-stream");
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
