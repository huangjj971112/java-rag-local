# Java RAG (Zhipu API)

本项目是一个低成本、本地部署的 Java RAG 示例：
- 模型服务：智谱 API（官方 `zai-sdk`）
- 文档类型：PDF / Word / TXT / Markdown
- 检索：Spring AI `SimpleVectorStore`

## 1. 环境准备

1) 安装 Java 17+  
2) 安装 Maven  
3) 准备智谱 API Key（`ZHIPU_API_KEY`）

macOS/Linux:
```bash
export ZHIPU_API_KEY=你的key
```

## 2. 放入文档

把 PDF/Word 文件放到：

`src/main/resources/docs`

## 3. 启动项目

```bash
mvn spring-boot:run
```

启动时会自动扫描 `docs` 目录并完成向量化入库。

## 4. 调用问答接口

```bash
curl "http://localhost:8080/api/chat?question=请总结文档的核心观点"
```

返回示例：
- `answer`: 最终回答
- `sources`: 命中的片段来源与内容

## 5. 关键配置（智谱）

配置文件：`src/main/resources/application.properties`

- `app.zhipu.api-key`：智谱 API Key（默认读取环境变量 `ZHIPU_API_KEY`）
- `app.zhipu.chat-model`：聊天模型（默认 `glm-4-flash`）
- `app.zhipu.embedding-model`：向量模型（默认 `embedding-3`）
- `app.rag.docs-path`：文档目录
- `app.rag.top-k`：检索条数
