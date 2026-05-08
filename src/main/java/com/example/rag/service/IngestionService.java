package com.example.rag.service;

import com.example.rag.RagProperties;
import com.example.rag.dto.response.DeleteFileResponseDTO;
import com.example.rag.dto.response.FileInfoDTO;
import com.example.rag.dto.response.UploadResponseDTO;
import com.example.rag.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Component
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final JdbcTemplate jdbcTemplate;

    public IngestionService(VectorStore vectorStore, RagProperties ragProperties, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ingest() throws Exception {

        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources(ragProperties.docsPath());

        if (resources.length == 0) {
            log.warn("没有找到文档: {}", ragProperties.docsPath());
            return;
        }

        List<Document> allDocs = new ArrayList<>();

        for (Resource resource : resources) {
            try {
                String fileName = Objects.requireNonNullElse(resource.getFilename(), "未知文件");
                String source = resource.getDescription();

                TikaDocumentReader reader = new TikaDocumentReader(resource);

                List<Document> docs = reader.get().stream()
                        .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
                        .map(doc -> new Document(
                                doc.getText(),
                                Map.of(
                                        "fileName", fileName,
                                        "source", source
                                )
                        ))
                        .toList();

                allDocs.addAll(docs);

                log.info("文档读取成功: {}, 段落数: {}", fileName, docs.size());

            } catch (Exception e) {
                log.warn("文档读取失败，已跳过: {}, 原因: {}",
                        resource.getFilename(),
                        e.getMessage());
            }
        }

        TokenTextSplitter splitter = new TokenTextSplitter(300, 50, 5, 10000, true);
        List<Document> splitDocs = splitter.apply(allDocs);

        // 应用层去重
        Set<String> seenHashes = new HashSet<>();
        List<Document> uniqueDocs = new ArrayList<>();

        for (Document doc : splitDocs) {
            String text = doc.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            String chunkHash = HashUtils.sha256(text);

            if (!seenHashes.add(chunkHash)) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("chunkHash", chunkHash);

            uniqueDocs.add(new Document(text, metadata));
        }

        log.info("切分完成: 原始 chunks={}, 去重后 chunks={}",
                splitDocs.size(),
                uniqueDocs.size());

        for (int i = 0; i < uniqueDocs.size(); i++) {
            Document doc = uniqueDocs.get(i);

            try {
                vectorStore.add(List.of(doc));

                log.info("chunk入库成功: {}/{} fileName={} hash={}",
                        i + 1,
                        uniqueDocs.size(),
                        doc.getMetadata().get("fileName"),
                        doc.getMetadata().get("chunkHash"));

                Thread.sleep(300);

            } catch (Exception e) {
                log.warn("chunk入库失败，已跳过: {}/{}, fileName={}, 原因: {}",
                        i + 1,
                        uniqueDocs.size(),
                        doc.getMetadata().get("fileName"),
                        e.getMessage());
            }
        }

        log.info("ingest done: 文档数={}, chunks={}", allDocs.size(), uniqueDocs.size());
    }

    public UploadResponseDTO ingestUpload(MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        byte[] bytes = file.getBytes();
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "未知文件");
        String fileHash = HashUtils.sha256(bytes);

        // 上传前去重
        if (existsByFileHash(fileHash)) {
            log.warn("文件已存在，跳过入库 fileName={}, fileHash={}", fileName, fileHash);

            return UploadResponseDTO.builder()
                    .success(false)
                    .fileName(fileName)
                    .fileHash(fileHash)
                    .chunks(0)
                    .message("文件已存在，请勿重复上传")
                    .build();
        }

        validateFileType(fileName);

        log.info("开始上传文档入库: fileName={}, size={}", fileName, file.getSize());

        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return fileName;
            }

            @Override
            public String getDescription() {
                return "upload:" + fileName;
            }
        };

        List<Document> docs = readResource(resource, fileName);

        List<Document> uniqueDocs = splitAndDeduplicate(docs, fileName, fileHash);

        int batchSize = 32;
        int successCount = 0;

        for (int i = 0; i < uniqueDocs.size(); i += batchSize) {
            List<Document> batch = uniqueDocs.subList(i, Math.min(i + batchSize, uniqueDocs.size()));

            try {
                vectorStore.add(batch);
                successCount += batch.size();

                log.info("批量入库完成: {}/{}",
                        Math.min(i + batchSize, uniqueDocs.size()),
                        uniqueDocs.size());

            } catch (Exception e) {
                log.error("批量入库失败: {}/{}，原因: {}",
                        i,
                        uniqueDocs.size(),
                        e.getMessage(),
                        e);
            }
        }

        if (successCount == 0) {
            throw new RuntimeException("文档入库失败：所有 chunk 都写入失败");
        }

        log.info("上传文档入库完成: fileName={}, successChunks={}, totalChunks={}",
                fileName, successCount, uniqueDocs.size());

        return UploadResponseDTO.builder()
                .success(true)
                .fileName(fileName)
                .fileHash(fileHash)
                .chunks(uniqueDocs.size())
                .message("上传并入库成功")
                .build();
    }

    private List<Document> readResource(Resource resource, String fileName) {
        try {
            String source = resource.getDescription();

            TikaDocumentReader reader = new TikaDocumentReader(resource);

            List<Document> docs = reader.get().stream()
                    .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
                    .map(doc -> new Document(
                            doc.getText(),
                            Map.of(
                                    "fileName", fileName,
                                    "source", source
                            )
                    ))
                    .toList();

            log.info("文档读取成功: {}, 段落数: {}", fileName, docs.size());

            return docs;

        } catch (Exception e) {
            throw new RuntimeException("文档读取失败: " + fileName, e);
        }
    }

    private List<Document> splitAndDeduplicate(List<Document> docs, String fileName, String fileHash) {
        TokenTextSplitter splitter = new TokenTextSplitter(300, 50, 5, 10000, true);
        List<Document> splitDocs = splitter.apply(docs);

        Set<String> seenHashes = new HashSet<>();
        List<Document> uniqueDocs = new ArrayList<>();

        int chunkIndex = 0;

        for (Document doc : splitDocs) {
            String text = doc.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            String chunkHash = HashUtils.sha256(text);

            if (!seenHashes.add(chunkHash)) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("chunkHash", chunkHash);
            metadata.put("fileName", fileName);
            metadata.put("fileHash", fileHash);
            metadata.put("chunkIndex", chunkIndex++);

            uniqueDocs.add(new Document(text, metadata));
        }

        log.info("切分完成: fileName={}, fileHash={}, 原始 chunks={}, 去重后 chunks={}",
                fileName, fileHash, splitDocs.size(), uniqueDocs.size());

        return uniqueDocs;
    }

    private void validateFileType(String fileName) {
        String lower = fileName.toLowerCase();

        if (!(lower.endsWith(".docx")
                || lower.endsWith(".pdf")
                || lower.endsWith(".txt"))) {
            throw new IllegalArgumentException("只支持 docx、pdf、txt 文件");
        }
    }

    public boolean existsByFileHash(String fileHash) {
        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata ->> 'fileHash' = ?";

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fileHash);

        return count != null && count > 0;
    }

    public DeleteFileResponseDTO deleteByFileHash(String fileHash) {

        if (fileHash == null || fileHash.isBlank()) {
            throw new IllegalArgumentException("fileHash不能为空");
        }

        String sql = "DELETE FROM vector_store WHERE metadata ->> 'fileHash' = ?";

        int rows = jdbcTemplate.update(sql, fileHash);

        if (rows == 0) {
            log.warn("删除失败：fileHash不存在或已被删除 fileHash={}", fileHash);

            return DeleteFileResponseDTO.builder()
                    .success(false)
                    .fileHash(fileHash)
                    .deletedRows(0)
                    .message("文件不存在或已删除")
                    .build();
        }

        log.info("删除成功 fileHash={}, 删除chunks={}", fileHash, rows);

        return DeleteFileResponseDTO.builder()
                .success(true)
                .fileHash(fileHash)
                .deletedRows(rows)
                .message("删除成功")
                .build();
    }

    public List<FileInfoDTO> listFiles() {
        String sql = """
            SELECT
                metadata ->> 'fileName' AS file_name,
                metadata ->> 'fileHash' AS file_hash,
                COUNT(*) AS chunks
            FROM vector_store
            WHERE metadata ->> 'fileHash' IS NOT NULL
            GROUP BY metadata ->> 'fileName', metadata ->> 'fileHash'
            ORDER BY metadata ->> 'fileName'
            """;

        List<FileInfoDTO> list = jdbcTemplate.query(sql, (rs, rowNum) ->
                FileInfoDTO.builder()
                        .fileName(rs.getString("file_name"))
                        .fileHash(rs.getString("file_hash"))
                        .chunks(rs.getInt("chunks"))
                        .build()
        );

        log.info("查询数据列表长度{}", list.size());

        return list;
    }
}