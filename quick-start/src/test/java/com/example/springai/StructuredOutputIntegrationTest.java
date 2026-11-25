package com.example.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大模型结构化输出测试：把模型返回的 JSON 文本转换成 POJO。
 *
 * <h3>原理（BeanOutputConverter 两段式，已从 1.1.8 字节码核实）</h3>
 * <pre>
 * 【第①步 · Prompt 侧：把 POJO 变成给模型的约束】
 *   BeanOutputConverter(Class) 构造时通过【反射】读取 POJO 的字段名、类型、嵌套结构，
 *   生成一份 JSON Schema（converter.getJsonSchema()），并拼装成格式指令 converter.getFormat()
 *   —— 内容 = JSON Schema + "只输出符合该 schema 的 JSON、不要输出其他文字"这类约束。
 *   ChatClient.entity() 内部会把 getFormat() 放进请求上下文（ChatClientAttributes.OUTPUT_FORMAT），
 *   组装 Prompt 时追加给模型；模型看到明确的字段/类型约定后，输出就"被引导"成目标结构。
 *
 * 【第②步 · 解析侧：把模型的 JSON 文本变回 POJO】
 *   converter.convert(原文) 做两件事：
 *     1) ResponseTextCleaner 剥掉模型偶尔包裹的 ```json / ``` 围栏和前后废话；
 *     2) Jackson ObjectMapper.readValue(json, type) 反序列化成 POJO 实例。
 *
 * 【本质】不是模型"原生"输出 POJO，也不是 function calling——
 *   是"prompt 注入 schema 约束 + 客户端 Jackson 反序列化"的两段式约定，
 *   因此对任何模型都通用；字段能否填对仍取决于模型遵循指令的能力。
 * </pre>
 *
 * 本测试用两个用例对照：
 * <ul>
 *   <li>{@link #manualTwoStepFlow()}：手动复刻上面两步（注入格式指令 → 拿原始 JSON → convert），
 *       能直观看到"注入了什么"和"模型原始输出是什么"；</li>
 *   <li>{@link #entityConvertsJsonToPojo()}：业务中推荐的写法——一行 {@code .entity(POJO.class)}
 *       完成同样的两步。</li>
 * </ul>
 */
@SpringBootTest
class StructuredOutputIntegrationTest {

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    /**
     * 结构化输出的目标 POJO —— 它本身就是"契约"：
     * BeanOutputConverter 反射读取下面这些字段名与类型生成 JSON Schema，
     * 模型被要求按此结构输出，最后再反序列化回这个类。
     * Jackson 需要无参构造 + getter/setter（也可改用 record，Jackson 同样支持）。
     */
    public static class BookInfo {
        private String title;
        private String author;
        private int year;
        private String genre;
        private Double rating;

        public BookInfo() {
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public Double getRating() {
            return rating;
        }

        public void setRating(Double rating) {
            this.rating = rating;
        }

        @Override
        public String toString() {
            return "BookInfo{title='" + title + "', author='" + author + "', year=" + year
                    + ", genre='" + genre + "', rating=" + rating + "}";
        }
    }

    /**
     * 用例1：手动复刻 entity() 的内部两步，完整展示原理。
     */
    @Test
    void manualTwoStepFlow() throws Exception {
        // —— 第①步：反射生成 JSON Schema 与格式指令（不发请求，纯本地计算）——
        BeanOutputConverter<BookInfo> converter = new BeanOutputConverter<>(BookInfo.class);
        System.out.println("【① 反射生成的 JSON Schema】\n" + converter.getJsonSchema());
        System.out.println("【① 注入 prompt 的格式指令 getFormat()】\n" + converter.getFormat());

        // —— 第②步：把格式指令作为 system prompt 注入（复刻 ChatClient.entity() 的内部行为）——
        String raw = ChatClient.create(deepSeekChatModel)
                .prompt()
                .system(converter.getFormat())
                .user("介绍余华的小说《活着》，按给定结构输出")
                .call()
                .content();
        System.out.println("【② 模型原始输出（JSON 文本）】\n" + raw);

        // —— 第③步：清理围栏 + Jackson 反序列化回 POJO ——
        BookInfo book = converter.convert(raw);
        System.out.println("【③ 转换后的 POJO】\n" + book);

        assertThat(raw)
                .as("原始输出应包含按 schema 约定的字段名")
                .contains("\"title\"");
        assertThat(book.getTitle()).contains("活着");
        assertThat(book.getAuthor()).isEqualTo("余华");
        assertThat(book.getYear()).isBetween(1900, 2030);
        assertThat(book.getGenre()).isNotBlank();
    }

    /**
     * 用例2：业务推荐写法——entity() 一行完成"注入 schema → 调用 → 反序列化"。
     * （entity 内部：new BeanOutputConverter(type) → getFormat() 写入请求上下文 OUTPUT_FORMAT
     *   → 执行调用 → convert(响应文本)，与用例1完全等价，见类注释原理。）
     */
    @Test
    void entityConvertsJsonToPojo() {
        BookInfo book = ChatClient.create(deepSeekChatModel)
                .prompt()
                .user("介绍余华的小说《活着》，按给定结构输出")
                .call()
                .entity(BookInfo.class);

        System.out.println("【entity() 一步到位 → POJO】\n" + book);

        assertThat(book.getTitle()).contains("活着");
        assertThat(book.getAuthor()).isEqualTo("余华");
        assertThat(book.getYear()).isBetween(1900, 2030);
        assertThat(book.getRating()).isNotNull();
    }

    /**
     * 用例3：验证用户消息里【不提结构要求】时结构化输出依然成立。
     * <p>
     * 结构约束来自 entity() 注入的格式指令（schema + "只输出 JSON"），
     * 与用户消息内容无关——用户侧的"按给定结构输出"只是冗余强化，去掉不影响结果。
     */
    @Test
    void entityWorksWithoutStructureHintInUserMessage() {
        BookInfo book = ChatClient.create(deepSeekChatModel)
                .prompt()
                .user("介绍余华的小说《活着》")   // 故意不提任何结构/格式要求
                .call()
                .entity(BookInfo.class);

        System.out.println("【无结构提示的 entity() → POJO】\n" + book);

        assertThat(book.getTitle()).contains("活着");
        assertThat(book.getAuthor()).isEqualTo("余华");
        assertThat(book.getYear()).isBetween(1900, 2030);
        assertThat(book.getGenre()).isNotBlank();
    }

    /**
     * 附：直接看 Jackson 视角的 schema（BeanOutputConverter 底层就是它生成的 Map）。
     * 不发请求，纯本地演示。
     */
    @Test
    void showGeneratedSchemaOnly() throws Exception {
        BeanOutputConverter<BookInfo> converter = new BeanOutputConverter<>(BookInfo.class);
        String schemaJson = new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(converter.getJsonSchemaMap());
        System.out.println("【POJO → JSON Schema（getJsonSchemaMap）】\n" + schemaJson);

        assertThat(converter.getJsonSchemaMap())
                .containsKey("properties")
                .containsKey("required");
    }

}
