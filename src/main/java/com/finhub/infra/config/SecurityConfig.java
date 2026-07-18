package com.finhub.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置：HTTP Basic Auth，用户名/密码通过环境变量注入。
 *
 * <p>文档访问策略：非 prod 放行 Knife4j/springdoc 文档路径（permitAll），
 * 便于测试环境查看与调试；prod 不放行（落到 authenticated），配合
 * application-prod.yml 的 springdoc 禁用形成 404/401 双保险。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Value("${finhub.auth.username:admin}")
    private String username;

    @Value("${finhub.auth.password:}") // 无默认值，线上必须注入
    private String password;

    /** 非 prod 放行的文档路径（Knife4j + springdoc 资源） */
    private static final String[] DOC_PATHS = {
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/webjars/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())          // REST API 禁用 CSRF
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health").permitAll();  // 健康检查免认证
                if (!isProd()) {
                    auth.requestMatchers(DOC_PATHS).permitAll();        // 非 prod 放行文档页
                }
                auth.anyRequest().authenticated();
            })
            .httpBasic(httpBasic -> {});            // HTTP Basic Auth（非 form login）

        return http.build();
    }

    /** 是否处于生产 profile（spring.profiles.active 含 prod） */
    private boolean isProd() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 线上密码由环境变量注入，开发环境默认值仅用于本地调试
        String effectivePassword = (password == null || password.isEmpty())
            ? "dev-pass" // 本地开发兜底（不应出现在线上）
            : password;

        return new InMemoryUserDetailsManager(
            User.builder()
                .username(username)
                .password(passwordEncoder().encode(effectivePassword))
                .roles("USER")
                .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}