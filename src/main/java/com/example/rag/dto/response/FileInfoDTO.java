package com.example.rag.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "已入库文件信息")
public class FileInfoDTO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件唯一标识")
    private String fileHash;

    @Schema(description = "chunk 数量")
    private int chunks;
}