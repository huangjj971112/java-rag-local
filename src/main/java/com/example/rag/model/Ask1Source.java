package com.example.rag.model;

public class Ask1Source {

    private String fileName;
    private String chapter;
    private String content;
    private double score;

    public Ask1Source(String fileName, String chapter, String content, double score) {
        this.fileName = fileName;
        this.chapter = chapter;
        this.content = content;
        this.score = score;
    }

    public String getFileName() {
        return fileName;
    }

    public String getChapter() {
        return chapter;
    }

    public String getContent() {
        return content;
    }

    public double getScore() {
        return score;
    }
}
