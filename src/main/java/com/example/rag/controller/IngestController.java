package com.example.rag.controller;

import com.example.rag.IngestionService;
import com.example.rag.dto.DeleteFileResponseDTO;
import com.example.rag.dto.UploadResponseDTO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import java.util.Map;

@RestController
@RequestMapping("ingest")
public class IngestController {

    private final IngestionService ingestionService;

    public IngestController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Operation(summary = "本地文档入库", description = "读取 classpath 下 docs 目录文档并入库")
    @GetMapping("/insert")
    public String ingest() throws Exception {
        ingestionService.ingest();
        return "ingest done";
    }

    @Operation(summary = "上传文档入库", description = "支持 docx/pdf/txt 上传并写入向量库")
    @PostMapping("/upload")
    public UploadResponseDTO upload(
            @Parameter(description = "上传文件", required = true)
            @RequestParam("file") MultipartFile file) throws Exception {

        return ingestionService.ingestUpload(file);
    }

    @PostMapping("/deleteFile")
    public DeleteFileResponseDTO deleteFile(@RequestParam("fileHash") String fileHash) {
        return ingestionService.deleteByFileHash(fileHash);
    }
}
