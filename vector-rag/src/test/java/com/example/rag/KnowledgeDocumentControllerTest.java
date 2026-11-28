package com.example.rag;

import com.example.rag.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识文档录入接口测试（MockMvc，走真实向量链路：Ollama 向量化 + Milvus 写入，无 LLM 成本）：
 * <pre>
 * 1. GET  /api/knowledge/formats   —— 格式清单含 pdf/docx/md/txt…
 * 2. POST /api/knowledge/upload    —— TXT 上传 → 分块入库 → 相似检索命中
 * 3. POST /api/knowledge/upload    —— 不支持的扩展名 → 400 且提示支持列表
 * 4. POST /api/knowledge/text      —— 纯文本录入
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RagService ragService;

    @Test
    void formatsListsSupportedDocumentTypes() throws Exception {
        mockMvc.perform(get("/api/knowledge/formats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formats[?(@.extension=='pdf')]").exists())
                .andExpect(jsonPath("$.formats[?(@.extension=='docx')]").exists())
                .andExpect(jsonPath("$.formats[?(@.extension=='md')]").exists())
                .andExpect(jsonPath("$.formats[?(@.extension=='txt')]").exists())
                .andExpect(jsonPath("$.formats[?(@.extension=='xlsx')]").exists());
    }

    @Test
    void uploadTxtDocumentThenSearchFindsIt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "phoenix-reimbursement.txt", "text/plain",
                ("知识库条目：Phoenix-777 项目的报销系统入口是 exp.phoenix.example.com，"
                        + "关键词 phoenix-reimbursement-2026，由财务组维护。").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/knowledge/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("txt"))
                .andExpect(jsonPath("$.parser").value("TextReader"))
                .andExpect(jsonPath("$.chunks", greaterThan(0)));

        List<String> hits = ragService.search("phoenix-reimbursement-2026", 3);
        assertThat(hits)
                .as("上传后应能按关键词检索到该文档")
                .isNotEmpty()
                .anyMatch(text -> text.contains("phoenix-reimbursement-2026"));
    }

    @Test
    void uploadUnsupportedExtensionIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.xyz", "application/octet-stream", new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/knowledge/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString(".xyz")))
                .andExpect(jsonPath("$.error", containsString("pdf")));
    }

    @Test
    void ingestPlainText() throws Exception {
        mockMvc.perform(post("/api/knowledge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"内部知识：Atlas-009 数据湖的保留策略是 90 天，关键词 atlas-retention-90。\",\"source\":\"manual-note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("manual-note"))
                .andExpect(jsonPath("$.chunks", greaterThan(0)));
    }

}
