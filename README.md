# spring-ai-parent

Spring Boot 3 + JDK 17 项目。

## 技术栈

- Spring Boot 3.5.16
- Java 17（`<java.version>17</java.version>`，高版本 JDK 也可编译运行）
- Maven（已含 Maven Wrapper：`mvnw` / `mvnw.cmd`）
- 依赖：spring-boot-starter-web、spring-boot-starter-test

## 构建与运行

```bash
# 编译 + 测试
./mvnw test

# 启动（默认端口 8080）
./mvnw spring-boot:run

# 打包
./mvnw clean package -DskipTests
java -jar target/spring-ai-parent-0.0.1-SNAPSHOT.jar
```

Windows 下将 `./mvnw` 换成 `mvnw.cmd` 即可。

## 项目结构

```
src/main/java/com/example/springai/SpringAiParentApplication.java   启动类
src/main/resources/application.properties                          配置
src/test/java/com/example/springai/SpringAiParentApplicationTests.java  上下文测试
```
