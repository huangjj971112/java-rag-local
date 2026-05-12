package com.example.rag.mapper;

import com.example.rag.dto.response.FileInfoDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VectorDocumentMapper {

    int countByFileHash(@Param("fileHash") String fileHash);

    int deleteByFileHash(@Param("fileHash") String fileHash);

    List<FileInfoDTO> listFiles();

    long countFiles();

    List<FileInfoDTO> pageFiles(@Param("offset") int offset,
                                @Param("pageSize") int pageSize);
}