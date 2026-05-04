package com.example.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "删除文件返回结果")
public class DeleteFileResponseDTO {

    @Schema(description = "是否删除成功")
    private boolean success;

    @Schema(description = "文件唯一标识（fileHash）")
    private String fileHash;

    @Schema(description = "删除的向量条数（chunk数量）")
    private int deletedRows;

    @Schema(description = "提示信息")
    private String message;
}