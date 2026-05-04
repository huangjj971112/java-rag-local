package com.example.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "上传文档返回结果")
public class UploadResponseDTO {

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件唯一标识")
    private String fileHash;

    @Schema(description = "切分后的 chunk 数量")
    private int chunks;

    @Schema(description = "提示信息")
    private String message;
}
