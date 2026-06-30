package com.finhub.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * <p>注入优先级：环境变量 > application.yml 默认值。
 * 线上必须通过 Docker 环境变量注入，禁止使用默认值。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${finhub.auth.username:admin}")
    private String username;

    @Value("${finhub.auth.password:}") // 无默认值，线上必须注入
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())          // REST API 禁用 CSRF
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()  // 健康检查免认证
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {});            // HTTP Basic Auth（非 form login）

        return http.build();
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