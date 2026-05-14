package com.example.oncall.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    private static final String BASE_DIR = "data";

    /**
     * 读取 data/ 目录下的指定 SOP 文档文件
     * @param fname 文件名，如 sop-001.html
     * @return 文件内容
     */
    public String readFile(String fname) {
        log.info("Tool readFile called with: {}", fname);

        // Normalize and validate path to prevent directory traversal
        Path basePath = Paths.get(BASE_DIR).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(fname).normalize();

        if (!targetPath.startsWith(basePath)) {
            throw new SecurityException("Access denied: path traversal detected");
        }

        if (!Files.exists(targetPath)) {
            return "[Error] File not found: " + fname;
        }

        try {
            String content = Files.readString(targetPath);
            log.info("Successfully read file: {} ({} chars)", fname, content.length());
            return content;
        } catch (IOException e) {
            log.error("Failed to read file: {}", fname, e);
            return "[Error] Failed to read file: " + e.getMessage();
        }
    }
}
