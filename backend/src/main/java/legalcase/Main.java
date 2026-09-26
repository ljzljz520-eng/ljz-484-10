package legalcase;

import com.sun.net.httpserver.HttpServer;
import legalcase.api.ApiHandler;
import legalcase.store.Store;
import legalcase.web.StaticHandler;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * 法律案例讲解稿系统 —— 服务入口（仅依赖 JDK，无需 Maven/Gradle）。
 *
 * 启动：java -cp out legalcase.Main [端口] [数据目录] [前端目录]
 * 默认：端口 8080，数据目录 backend/data，前端目录 ../frontend
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length >= 1 && !args[0].isBlank() ? Integer.parseInt(args[0]) : 8080;
        Path dataDir = Paths.get(args.length >= 2 && !args[1].isBlank() ? args[1] : "data");
        Path frontendDir = Paths.get(args.length >= 3 && !args[2].isBlank() ? args[2] : "../frontend");

        Store store = new Store(dataDir);
        store.init();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(store));
        if (Files.isDirectory(frontendDir)) {
            server.createContext("/", new StaticHandler(frontendDir));
            System.out.println("已挂载前端静态目录: " + frontendDir.toAbsolutePath());
        } else {
            System.out.println("未找到前端目录（" + frontendDir.toAbsolutePath()
                    + "），仅提供 /api 接口；可用独立静态服务器打开 frontend/");
        }
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("==========================================================");
        System.out.println(" 法律案例讲解稿系统 服务已启动");
        System.out.println(" 数据文件: " + dataDir.toAbsolutePath().resolve("cases.json"));
        System.out.println(" 接口地址: http://localhost:" + port + "/api/cases");
        System.out.println(" 网页入口: http://localhost:" + port + "/");
        System.out.println(" 角色头  : X-Role: teacher（教师） / 不传或 student（学生）");
        System.out.println("==========================================================");
    }
}
