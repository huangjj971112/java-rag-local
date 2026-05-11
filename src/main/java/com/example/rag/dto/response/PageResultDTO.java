package com.example.rag.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "分页返回结果")
public class PageResultDTO<T> {

    @Schema(description = "总数量")
    private long total;

    @Schema(description = "当前页码")
    private int pageNum;

    @Schema(description = "每页数量")
    private int pageSize;

    @Schema(description = "当前页数据")
    private List<T> records;
}