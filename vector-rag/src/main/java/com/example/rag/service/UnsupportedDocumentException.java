package com.example.rag.service;

import java.util.Set;

/**
 * 不支持的文档格式（由 KnowledgeDocumentController 转为 400 响应）。
 */
public class UnsupportedDocumentException extends RuntimeException {

    public UnsupportedDocumentException(String extension, Set<String> supported) {
        super("不支持的文件格式: ." + extension + "，支持的格式: " + String.join(", ", supported));
    }

}
