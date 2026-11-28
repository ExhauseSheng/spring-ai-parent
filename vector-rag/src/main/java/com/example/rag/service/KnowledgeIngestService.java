package com.example.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 知识文档录入服务：文件 → 解析成 Document → 分块 → 向量化 → 写入 Milvus。
 *
 * <h3>支持的主流格式与解析器</h3>
 * <pre>
 * .txt / .log / .csv         TextReader（纯文本，按行读取，classpath 已有无需额外依赖）
 * .md / .markdown            MarkdownDocumentReader（保留标题层级等结构）
 * .pdf                       TikaDocumentReader（Apache Tika，提取文本与基本元数据）
 * .doc / .docx               TikaDocumentReader（经 POI 解析 Word）
 * .xls / .xlsx               TikaDocumentReader（经 POI 解析 Excel）
 * .ppt / .pptx               TikaDocumentReader（经 POI 解析 PPT）
 * .html / .htm               TikaDocumentReader（提取正文，剥离标签）
 * .rtf                       TikaDocumentReader
 * </pre>
 * 录入管线：Reader 解析 → {@link TokenTextSplitter} 按 ~200 token 分块
 * → vectorStore.add()（内部调用 Ollama qwen3-embedding 逐块向量化）→ 写入 Milvus 集合。
 * 分块的意义：检索粒度更细、embedding 更准，避免整篇文档压成一个向量。
 */
@Service
public class KnowledgeIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestService.class);

    /** 扩展名 → 解析器（LinkedHashMap 保持展示顺序） */
    private static final Map<String, String> SUPPORTED_FORMATS = new LinkedHashMap<>();

    static {
        SUPPORTED_FORMATS.put("txt", "TextReader");
        SUPPORTED_FORMATS.put("log", "TextReader");
        SUPPORTED_FORMATS.put("csv", "TextReader");
        SUPPORTED_FORMATS.put("md", "MarkdownDocumentReader");
        SUPPORTED_FORMATS.put("markdown", "MarkdownDocumentReader");
        SUPPORTED_FORMATS.put("pdf", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("doc", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("docx", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("xls", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("xlsx", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("ppt", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("pptx", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("html", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("htm", "TikaDocumentReader");
        SUPPORTED_FORMATS.put("rtf", "TikaDocumentReader");
    }

    private final VectorStore vectorStore;

    /** 约 200 token 一块，保留分隔符（段落边界） */
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(200)
            .withKeepSeparator(true)
            .build();

    public KnowledgeIngestService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 上传文件录入：按扩展名分发解析器 */
    public IngestSummary ingest(MultipartFile file) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed");
        String ext = extensionOf(filename);
        String parser = SUPPORTED_FORMATS.get(ext);
        if (parser == null) {
            throw new UnsupportedDocumentException(ext.isEmpty() ? "(无扩展名)" : ext, SUPPORTED_FORMATS.keySet());
        }

        List<Document> documents = parse(ext, file.getBytes(), filename);
        return index(documents, filename, ext, parser);
    }

    /** 纯文本录入（供 /api/knowledge/text，兼容原 /api/rag/documents 语义） */
    public IngestSummary ingestText(String text, String source) {
        String src = (source == null || source.isBlank()) ? "inline-text" : source;
        // 注意：TextReader(String) 的参数是 classpath 资源路径而非文本内容，须用 Resource 传内容
        List<Document> documents = new TextReader(resourceOf(text.getBytes(StandardCharsets.UTF_8), src)).get();
        return index(documents, src, "txt", "TextReader");
    }

    public Map<String, String> supportedFormats() {
        return SUPPORTED_FORMATS;
    }

    private List<Document> parse(String ext, byte[] bytes, String filename) throws IOException {
        Resource resource = resourceOf(bytes, filename);
        return switch (ext) {
            case "txt", "log", "csv" -> new TextReader(resource).get();
            // MarkdownDocumentReader 的便捷构造器接收 Markdown 文本本身
            case "md", "markdown" -> new MarkdownDocumentReader(new String(bytes, StandardCharsets.UTF_8)).get();
            default -> new TikaDocumentReader(resource).get(); // pdf / office / html / rtf
        };
    }

    private IngestSummary index(List<Document> documents, String source, String format, String parser) {
        documents.forEach(doc -> doc.getMetadata().put("source", source));
        List<Document> chunks = splitter.split(documents);
        vectorStore.add(chunks);
        log.info("知识入库完成: {}（.{} / {}）原始文档 {} 个 → 分块 {} 个",
                source, format, parser, documents.size(), chunks.size());
        return new IngestSummary(source, format, parser, documents.size(), chunks.size());
    }

    private static Resource resourceOf(byte[] bytes, String filename) {
        // 匿名子类提供文件名，便于 Tika 按扩展名提示检测类型
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 录入结果：源文件、格式、解析器、原始文档数、分块数（=写入 Milvus 的向量数） */
    public record IngestSummary(String source, String format, String parser, int documents, int chunks) {
    }

}
