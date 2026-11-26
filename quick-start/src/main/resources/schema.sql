-- Spring Security standard tables (JdbcUserDetailsManager default queries match this schema)
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) NOT NULL PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS authorities (
    username VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    PRIMARY KEY (username, authority),
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users(username)
);

-- Tool catalog: all @Tool methods known to the application
CREATE TABLE IF NOT EXISTS sys_tool (
    tool_name VARCHAR(100) NOT NULL PRIMARY KEY,
    description VARCHAR(255)
);

-- Role -> tool grants. role matches authorities.authority, tool_name matches sys_tool
CREATE TABLE IF NOT EXISTS sys_role_tool (
    role VARCHAR(50) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (role, tool_name),
    CONSTRAINT fk_srt_tool FOREIGN KEY (tool_name) REFERENCES sys_tool(tool_name)
);
