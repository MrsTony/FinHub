package com.finhub.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 启动面板：服务启动后控制台打印中文 ASCII 面板。
 *
 * <p>非 prod：打印本地+局域网 /doc.html 链接、API 端点、Basic Auth 凭据。
 * prod：仅打印一行「Knife4j 已禁用」，不打印链接与凭据。</p>
 *
 * <p>面板字符串构建为纯静态方法 {@link #buildPanel}，便于单测。</p>
 */
@Slf4j
@Component
public class StartupBanner implements ApplicationRunner {

    private final Environment environment;

    @Value("${spring.application.name:FinHub}")
    private String appName;

    @Value("${finhub.version:0.1.0}")
    private String version;

    @Value("${server.port:8080}")
    private int port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public StartupBanner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = isProd();
        String lanIp = resolveLanIp();
        String panel = buildPanel(prod, appName, version, port, contextPath, lanIp);
        log.info("\n{}", panel);
    }

    /**
     * 构建启动面板字符串（纯函数，无副作用，便于单测）。
     *
     * @param isProd      是否生产环境
     * @param appName     应用名
     * @param version     版本
     * @param port        端口
     * @param contextPath 上下文路径（可为空）
     * @param lanIp       局域网 IP（不可达时传 "N/A"）
     * @return 面板字符串
     */
    public static String buildPanel(boolean isProd, String appName, String version,
                                    int port, String contextPath, String lanIp) {
        String path = (contextPath == null || contextPath.isBlank()) ? "" : contextPath;
        if (isProd) {
            return """
                    ===================================================
                      %s 启动完成（env=prod，Knife4j 已禁用）
                    ===================================================""".formatted(appName);
        }
        String local = "http://localhost:" + port + path + "/doc.html";
        String lan = "http://" + lanIp + ":" + port + path + "/doc.html";
        String api = "http://localhost:" + port + path + "/api/transactions/import";
        return """
                ===================================================
                  %s  v%s
                --------------------------------------------------
                  环境         : 非 prod（Knife4j 可用）
                  API 文档     : %s
                  局域网文档   : %s
                  API 端点     : %s
                  Basic Auth  : admin/dev-pass
                ===================================================""".formatted(
                appName, version, local, lan, api);
    }

    private boolean isProd() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    /** 解析局域网 IP，失败回退 "N/A" */
    private String resolveLanIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "N/A";
        }
    }
}