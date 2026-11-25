package com.example.springai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地自定义 Tools —— 模型自己拿不到的数据/能力，由模型发起 tool_call、在本进程执行。
 *
 * <h3>原理（Spring AI 工具调用闭环，对应 1.1.8 实现）</h3>
 * <pre>
 * ① 注册与"可见"：ChatClient.prompt().tools(this) 会反射扫描本类的 @Tool 方法，
 *    生成工具清单（工具名、方法描述、参数 JSON Schema）随 prompt 发给模型——
 *    模型据此知道"有哪些工具、何时该用"。
 * ② 模型决策：当问题需要外部信息时，模型本轮不输出正文，而是返回 tool_calls
 *    （工具名 + JSON 参数）；不需要工具时直接输出正文，闭环结束。
 * ③ 本地执行：Spring AI 按 JSON 参数在【本进程】反射调用对应 @Tool 方法
 *    ——这一步完全不经过模型，就是"本地自定义 tools"的执行点（可在方法里查库、调接口等）。
 * ④ 回传与循环：方法返回值转成 tool_result 消息回传模型，模型结合结果继续生成；
 *    若再次请求工具则重复②③④，直到不再请求工具，最终 content 返回给调用方。
 *    默认 internalToolExecutionEnabled=true，整个闭环由 Spring AI 自动完成。
 * </pre>
 *
 * 注意：@Tool 方法的入参会变成给模型的参数 schema（用 {@link ToolParam} 补充描述可提高
 * 模型填参准确率）；返回值会作为工具结果喂回模型（保持简短、信息密度高为佳）。
 */
@Component
public class LocalTools {

    private static final Logger log = LoggerFactory.getLogger(LocalTools.class);

    /** 工具执行轨迹（测试断言"本地确实执行过"用） */
    private final List<String> callTrail = new CopyOnWriteArrayList<>();

    @Tool(description = "查询指定城市的当前天气状况与温度。当用户询问天气时必须调用本工具，严禁凭空编造天气。")
    public String getCurrentWeather(@ToolParam(description = "城市名，例如：北京") String city) {
        // 模拟本地数据源（真实项目里这里是查数据库 / 调内部接口）
        String result = city + "：晴，气温25°C，东南风3级";
        callTrail.add("getCurrentWeather(" + city + ")");
        log.info("本地工具执行 getCurrentWeather({}) -> {}", city, result);
        return result;
    }

    @Tool(description = "查询指定用户的VIP等级与有效期。当用户询问VIP/会员信息时必须调用本工具，严禁凭空编造。")
    public String queryVipLevel(@ToolParam(description = "用户ID，例如：U10086") String userId) {
        String result = "用户" + userId + " 是黄金VIP，有效期至2027-12-31";
        callTrail.add("queryVipLevel(" + userId + ")");
        log.info("本地工具执行 queryVipLevel({}) -> {}", userId, result);
        return result;
    }

    public List<String> getCallTrail() {
        return List.copyOf(callTrail);
    }

    public void resetCallTrail() {
        callTrail.clear();
    }

}
