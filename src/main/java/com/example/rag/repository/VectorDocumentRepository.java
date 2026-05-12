package com.example.rag.repository;

import com.example.rag.dto.response.FileInfoDTO;
import com.example.rag.dto.response.PageResultDTO;
import com.example.rag.mapper.VectorDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Service
public class VectorDocumentRepository {

    private final VectorDocumentMapper vectorDocumentMapper;

    public boolean existsByFileHash(String fileHash) {
        return vectorDocumentMapper.countByFileHash(fileHash) > 0;
    }

    public int deleteByFileHash(String fileHash) {
        return vectorDocumentMapper.deleteByFileHash(fileHash);
    }

    public List<FileInfoDTO> listFiles() {
        return vectorDocumentMapper.listFiles();
    }

    public PageResultDTO<FileInfoDTO> pageFiles(int pageNum, int pageSize) {
        if (pageNum < 1) {
            pageNum = 1;
        }

        if (pageSize < 1) {
            pageSize = 10;
        }

        int offset = (pageNum - 1) * pageSize;

        long total = vectorDocumentMapper.countFiles();

        List<FileInfoDTO> records = vectorDocumentMapper.pageFiles(offset, pageSize);

        return PageResultDTO.<FileInfoDTO>builder()
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .records(records)
                .build();
    }
}