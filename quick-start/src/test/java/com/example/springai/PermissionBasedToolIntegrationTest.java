package com.example.springai;

import com.example.springai.tools.LocalTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 按权限动态下发 Tools 的集成测试（HTTP Basic + MySQL 权限表全链路）：
 * <pre>
 * alice（ROLE_WEATHER）→ 只能用 getCurrentWeather
 * bob  （ROLE_VIP）    → 只能用 queryVipLevel
 * </pre>
 * 同一个问题"查VIP+查天气"，两个用户得到不同的工具集与不同的回复内容。
 * 需要 MySQL（spring.sql.init 已建表种子数据）与 DeepSeek API Key。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PermissionBasedToolIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LocalTools localTools;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String chat(String username, String password, String message) {
        ResponseEntity<Map> response = rest.withBasicAuth(username, password)
                .postForEntity(url(), Map.of("message", message), Map.class);
        assertThat(response.getStatusCode())
                .as("认证用户应得到 200")
                .isEqualTo(HttpStatus.OK);
        return String.valueOf(response.getBody().get("reply"));
    }

    private String url() {
        return "http://localhost:" + port + "/api/chat/tools/dynamic";
    }

    @Test
    void aliceOnlyGetsWeatherTool() {
        localTools.resetCallTrail();

        String reply = chat("alice", "password", "查询用户 U10086 的VIP等级，再查一下北京的天气");

        System.out.println("【alice·工具轨迹】" + localTools.getCallTrail());
        System.out.println("【alice·回复】" + reply);

        assertThat(localTools.getCallTrail())
                .as("alice 有天气权限，本地应执行 getCurrentWeather")
                .anyMatch(t -> t.contains("getCurrentWeather") && t.contains("北京"));
        assertThat(localTools.getCallTrail())
                .as("alice 无 VIP 权限，queryVipLevel 不应出现在工具集，更不会执行")
                .noneMatch(t -> t.contains("queryVipLevel"));
        assertThat(reply)
                .as("回复应含天气工具数据，且不含 VIP 工具数据")
                .contains("25")
                .doesNotContain("黄金");
    }

    @Test
    void bobOnlyGetsVipTool() {
        localTools.resetCallTrail();

        String reply = chat("bob", "password", "查询用户 U10086 的VIP等级，再查一下北京的天气");

        System.out.println("【bob·工具轨迹】" + localTools.getCallTrail());
        System.out.println("【bob·回复】" + reply);

        assertThat(localTools.getCallTrail())
                .as("bob 有 VIP 权限，本地应执行 queryVipLevel")
                .anyMatch(t -> t.contains("queryVipLevel") && t.contains("U10086"));
        assertThat(localTools.getCallTrail())
                .as("bob 无天气权限，getCurrentWeather 不应出现在工具集")
                .noneMatch(t -> t.contains("getCurrentWeather"));
        assertThat(reply)
                .as("回复应含 VIP 工具数据，且不含天气工具数据")
                .contains("黄金")
                .doesNotContain("25");
    }

    @Test
    void dynamicEndpointRequiresAuthentication() {
        ResponseEntity<String> response = rest.postForEntity(url(), Map.of("message", "hi"), String.class);

        System.out.println("【未认证】HTTP " + response.getStatusCode());
        assertThat(response.getStatusCode())
                .as("未认证请求应被 Spring Security 拦截")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

}
