package com.example.rag.web;

import com.example.rag.service.KnowledgeIngestService;
import com.example.rag.service.UnsupportedDocumentException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识文档录入专用接口（职责单一：文档 → 向量库）：
 * <ul>
 *   <li>GET  /api/knowledge/formats —— 支持的文档格式与解析器清单</li>
 *   <li>POST /api/knowledge/upload   —— multipart 上传文档（PDF/Word/Excel/PPT/MD/TXT/HTML…）</li>
 *   <li>POST /api/knowledge/text     —— 直接提交纯文本</li>
 * </ul>
 * 解析与分块逻辑见 {@link KnowledgeIngestService}。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeDocumentController {

    private final KnowledgeIngestService ingestService;

    public KnowledgeDocumentController(KnowledgeIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** 支持的格式清单 */
    @GetMapping("/formats")
    public FormatsReply formats() {
        List<FormatView> formats = ingestService.supportedFormats().entrySet().stream()
                .map(e -> new FormatView(e.getKey(), e.getValue()))
                .toList();
        return new FormatsReply(formats);
    }

    /** 文档上传录入：解析 → 分块 → 向量化 → 写 Milvus */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            KnowledgeIngestService.IngestSummary summary = ingestService.ingest(file);
            return ResponseEntity.ok(toReply(summary));
        } catch (UnsupportedDocumentException | IOException e) {
            return ResponseEntity.badRequest().body(new ErrorReply(e.getMessage()));
        }
    }

    /** 纯文本录入 */
    @PostMapping("/text")
    public ResponseEntity<?> text(@RequestBody TextRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorReply("text 不能为空"));
        }
        KnowledgeIngestService.IngestSummary summary = ingestService.ingestText(request.text(), request.source());
        return ResponseEntity.ok(toReply(summary));
    }

    private static UploadReply toReply(KnowledgeIngestService.IngestSummary s) {
        return new UploadReply(s.source(), s.format(), s.parser(), s.documents(), s.chunks());
    }

    public record FormatView(String extension, String parser) {
    }

    public record FormatsReply(List<FormatView> formats) {
    }

    public record TextRequest(String text, String source) {
    }

    public record UploadReply(String source, String format, String parser, int documents, int chunks) {
    }

    public record ErrorReply(String error) {
    }

}
