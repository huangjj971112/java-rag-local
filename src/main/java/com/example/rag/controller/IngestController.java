package com.example.rag.controller;

import com.example.rag.IngestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("ingest")
public class IngestController {

    private final IngestionService ingestionService;

    public IngestController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }
    @GetMapping("/insert")
    public String ingest() throws Exception {
        ingestionService.ingest();
        return "ingest done";
    }
}
