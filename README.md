# spring-ai-parent

Spring AI 多模块项目（Spring Boot 3 + JDK 17）。

## 技术栈

- Spring Boot 3.5.16
- Spring AI 1.1.8（BOM 统一管理版本）
- Spring AI Alibaba 1.1.2.3（阿里云百炼 / DashScope）
- Java 17（`<java.version>17</java.version>`，高版本 JDK 也可编译运行）
- Maven（已含 Maven Wrapper：`mvnw` / `mvnw.cmd`）

## 模块结构

```
spring-ai-parent/            父工程（packaging=pom，统一依赖与版本管理）
└── quick-start/             快速上手模块，接入 DeepSeek 模型能力
    ├── src/main/java/com/example/springai/
    │   ├── QuickStartApplication.java          启动类
    │   ├── service/ChatService.java            DeepSeek 对话服务（Spring AI ChatClient）
    │   ├── service/BailianChatService.java     阿里云百炼对话服务（Qwen）
    │   ├── web/ChatController.java             DeepSeek 接口 POST /api/chat（含流式 /stream）
    │   └── web/BailianChatController.java      百炼接口 POST /api/bailian/chat
    └── src/main/resources/application.properties   DeepSeek + 百炼配置
```

## 前置条件

1. **DeepSeek**：在 [DeepSeek 开放平台](https://platform.deepseek.com) 创建 API Key
2. **阿里云百炼**（可选）：在 [百炼控制台](https://bailian.console.aliyun.com) 创建 API Key
3. 设置环境变量：

```bash
export DEEPSEEK_API_KEY=sk-xxxxxxxx        # Windows: set DEEPSEEK_API_KEY=sk-xxxxxxxx
export DASHSCOPE_API_KEY=sk-xxxxxxxx       # 可选；未设置时百炼功能跳过，不影响 DeepSeek
```

如需走代理访问 DeepSeek，给 JVM 加参数：`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897`（百炼国内直连即可）。

## 构建与运行

```bash
# 编译 + 测试
./mvnw test

# 启动 quick-start 模块（默认端口 8080）
./mvnw -pl quick-start spring-boot:run

# 打包
./mvnw clean package -DskipTests
java -jar quick-start/target/quick-start-0.0.1-SNAPSHOT.jar
```

Windows 下将 `./mvnw` 换成 `mvnw.cmd` 即可。

## 调用示例

```bash
# DeepSeek 对话
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，介绍一下你自己"}'

# 阿里云百炼（Qwen）对话
curl -X POST http://localhost:8080/api/bailian/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，介绍一下你自己"}'
```

返回：

```json
{"reply": "..."}
```

## 说明

- DeepSeek 模型可通过 `spring.ai.deepseek.chat.options.model` 切换：`deepseek-chat`（对话）/ `deepseek-reasoner`（推理）
- 百炼模型可通过 `spring.ai.dashscope.chat.options.model` 切换（默认 `qwen-plus`）
- 应用中同时存在两个 `ChatModel` bean（`deepSeekChatModel`、`dashScopeChatModel`），服务层注入时需使用具体类型
- 新增子模块：在根 `pom.xml` 的 `<modules>` 中登记，并以 `spring-ai-parent` 为 parent 创建模块即可
