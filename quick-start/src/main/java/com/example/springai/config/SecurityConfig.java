package com.example.springai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

import javax.sql.DataSource;

/**
 * Spring Security 配置：HTTP Basic + 无状态会话，用户/角色存 MySQL。
 * <p>
 * 只保护"按权限动态下发工具"的接口（/api/chat/tools/dynamic），其余接口保持开放；
 * 用户与角色由 {@code JdbcUserDetailsManager} 查 spring.sql.init 建的 users/authorities 表
 * （默认 SQL 与 schema.sql 完全匹配），密码为 BCrypt。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST 无状态接口，禁用 CSRF（否则 POST 会被拦截）
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/chat/tools/dynamic").authenticated()
                        .anyRequest().permitAll())
                // HTTP Basic：curl -u alice:password
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /** JDBC 用户库：从 MySQL 读用户与角色 */
    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
