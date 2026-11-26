package com.example.springai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具权限服务：从 MySQL 查询某用户被授权的工具名。
 * <p>
 * 数据链路（三张表，见 schema.sql / data.sql）：
 * <pre>
 * users（用户）→ authorities（用户-角色）→ sys_role_tool（角色-工具授权）→ sys_tool（工具目录）
 * </pre>
 * ChatService 用返回的工具名过滤 ToolCallback，实现"按权限动态下发 Tools"。
 */
@Service
public class ToolPermissionService {

    private static final Logger log = LoggerFactory.getLogger(ToolPermissionService.class);

    private final JdbcTemplate jdbcTemplate;

    public ToolPermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 解析用户被授权的工具名：授权表 JOIN 角色表 JOIN 工具目录（同时校验工具存在于目录中）。
     */
    public List<String> getAllowedToolNames(String username) {
        List<String> toolNames = jdbcTemplate.query(
                "SELECT rt.tool_name FROM sys_role_tool rt "
                        + "JOIN authorities a ON a.authority = rt.role "
                        + "JOIN sys_tool t ON t.tool_name = rt.tool_name "
                        + "WHERE a.username = ?",
                (rs, rowNum) -> rs.getString("tool_name"),
                username);
        log.info("用户 {} 授权的工具: {}", username, toolNames);
        return toolNames;
    }

}
