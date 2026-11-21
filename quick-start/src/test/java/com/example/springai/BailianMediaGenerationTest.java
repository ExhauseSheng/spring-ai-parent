package com.example.springai;

import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import com.alibaba.cloud.ai.dashscope.video.DashScopeVideoModel;
import com.alibaba.cloud.ai.dashscope.video.DashScopeVideoOptions;
import com.alibaba.cloud.ai.dashscope.video.VideoPrompt;
import com.alibaba.cloud.ai.dashscope.video.VideoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阿里云百炼媒体生成集成测试：文生图、文生语音（TTS）、文生视频。
 * <p>
 * 需要环境变量 {@code DASHSCOPE_API_KEY}；未设置时测试自动跳过。
 * 文生视频为异步任务（内部轮询），通常需要 1~3 分钟。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class BailianMediaGenerationTest {

    @Autowired
    private DashScopeImageModel imageModel;

    @Autowired
    private DashScopeAudioSpeechModel speechModel;

    @Autowired
    private DashScopeVideoModel videoModel;

    @Test
    void generateImageFromText() {
        ImagePrompt prompt = new ImagePrompt(
                "一只戴红色围巾的卡通小猫在雪地里，扁平插画风格",
                DashScopeImageOptions.builder()
                        .model("wanx-v1")
                        .withSize("1024*1024")
                        .n(1)
                        .build());

        ImageResponse response = imageModel.call(prompt);
        System.out.println("文生图 call() 原始响应: " + response);

        assertThat(response.getResults())
                .as("文生图应返回结果")
                .isNotEmpty();

        var image = response.getResult().getOutput();
        String location = image.getUrl() != null ? image.getUrl() : "(base64 " + image.getB64Json().length() + " chars)";
        System.out.println("文生图结果: " + location);

        assertThat(image.getUrl() != null || (image.getB64Json() != null && !image.getB64Json().isEmpty()))
                .as("应返回图片 URL 或 base64 内容")
                .isTrue();
    }

    @Test
    void synthesizeSpeechFromText() throws Exception {
        // cosyvoice 系列只支持流式合成，不能用 call() 同步调用
        List<byte[]> chunks = speechModel
                .stream("你好，这是阿里云百炼语音合成测试。",
                        DashScopeAudioSpeechOptions.builder()
                                .model("cosyvoice-v3-flash")
                                .voice("longanyang")
                                .build())
                .collectList()
                .block(Duration.ofSeconds(60));

        assertThat(chunks).as("流式语音应有数据块").isNotEmpty();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            buffer.write(chunk, 0, chunk.length);
        }
        byte[] audio = buffer.toByteArray();
        assertThat(audio).as("语音字节应非空").isNotEmpty();

        Path out = Path.of("target", "bailian-tts-test.mp3");
        Files.createDirectories(out.getParent());
        Files.write(out, audio);

        System.out.println("文生语音已保存: " + out.toAbsolutePath() + " (" + audio.length + " bytes, "
                + chunks.size() + " 个流式数据块)");
    }

    @Test
    void generateVideoFromText() {
        VideoResponse response = videoModel.call(VideoPrompt.builder()
                .content("一只戴红色围巾的卡通小猫在雪地里奔跑，动画风格")
                .options(DashScopeVideoOptions.builder()
                        .model("wanx2.1-t2v-turbo")
                        .input(DashScopeVideoOptions.InputOptions.builder()
                                .prompt("一只戴红色围巾的卡通小猫在雪地里奔跑，动画风格")
                                .build())
                        .build())
                .build());

        var output = response.getResult().getOutput();
        System.out.println("文生视频 taskStatus=" + output.taskStatus() + ", videoUrl=" + output.videoUrl());

        assertThat(output.videoUrl())
                .as("视频任务完成后应返回 videoUrl")
                .isNotBlank();
    }

}
