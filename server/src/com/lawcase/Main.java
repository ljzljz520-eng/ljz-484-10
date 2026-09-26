package com.lawcase;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * 法律案例讲解稿系统 —— Java 服务入口。
 * 仅依赖 JDK 11+（推荐 17），无需 Maven/Gradle。
 *
 * 环境变量配置：
 *   PORT          监听端口，默认 8080
 *   DATA_FILE     数据 JSON 文件路径，默认 data/cases.json（相对于工作目录）
 *   WEB_DIR       网页静态资源目录，默认 ../web
 *   TEACHER_TOKEN 教师端口令，默认 teacher123
 */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        Path dataFile = Paths.get(env("DATA_FILE", "data/cases.json")).toAbsolutePath().normalize();
        Path webDir = Paths.get(env("WEB_DIR", "../web")).toAbsolutePath().normalize();
        if (!Files.isDirectory(webDir)) {
            Path alt = Paths.get("web").toAbsolutePath().normalize();
            if (Files.isDirectory(alt)) webDir = alt;
        }
        String teacherToken = env("TEACHER_TOKEN", "teacher123");

        Store store = new Store(dataFile);
        store.load();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/", new Api(store, teacherToken));
        server.createContext("/", new StaticFiles(webDir));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("================================================");
        System.out.println(" 法律案例讲解稿系统 已启动");
        System.out.println(" 学生端:   http://localhost:" + port + "/");
        System.out.println(" 教师端:   http://localhost:" + port + "/teacher.html");
        System.out.println(" 数据文件: " + dataFile);
        System.out.println(" 网页目录: " + webDir);
        System.out.println(" 健康检查: http://localhost:" + port + "/api/health");
        System.out.println("================================================");
    }

    static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
