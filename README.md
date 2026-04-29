# Java RAG Local

> 基于 Spring AI + 智谱 API 的本地知识库 RAG 问答系统（低成本的 Java 实现）

## 简介

本项目演示如何用 Java + Spring Boot 构建一个**低成本的 RAG（检索增强生成）** 系统：

- ✅ **本地文档 → 向量化 → 检索 → LLM 问答** 全流程
- ✅ 使用 **智谱 GLM API（glm-4-flash）** 作为底层大模型，Embedding 也用智谱
- ✅ 文档解析使用 Apache Tika，支持 `.docx`、`.pdf`、`.txt`、`.md` 等多种格式
- ✅ 向量库使用内存级 **SimpleVectorStore**，零额外部署成本
- ✅ 文档自动切分（`TokenTextSplitter`），启动即自动导入知识库

## 技术栈

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 框架 | Spring Boot 3.3.4 | 最新稳定版 |
| RAG 框架 | Spring AI 1.0.6 | 官方 RAG 抽象层 |
| 大模型 | 智谱 GLM-4-Flash | 免费额度，性价比高 |
| Embedding | 智谱 embedding-3 | 文本向量化 |
| 文档解析 | Apache Tika | 支持多种格式 |
| 向量库 | SimpleVectorStore | 内存级，无需额外服务 |

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- 智谱开放平台 API Key（[https://open.bigmodel.cn](https://open.bigmodel.cn)）

### 1. 获取 API Key

1. 注册 [智谱开放平台](https://open.bigmodel.cn)
2. 创建 API Key
3. `glm-4-flash` 模型有大量免费额度

### 2. 配置环境变量

```bash
export ZHIPU_API_KEY=your_api_key_here
```

或在 `application.properties` 中直接填写（不推荐提交到 Git）：

```properties
app.zhipu.api-key=your_api_key_here
```

### 3. 准备知识文档

将你的文档文件放在 `src/main/resources/docs/` 目录下，支持：

- `.docx` — Word 文档
- `.pdf` — PDF 文件
- `.txt` — 纯文本
- `.md` — Markdown 文件

> 默认路径是 `classpath:/docs/*.docx`，可通过 `app.rag.docs-path` 配置修改。

### 4. 启动服务

```bash
mvn spring-boot:run
```

启动时自动完成：
1. 读取 `docs/` 目录下的所有文档
2. 使用 Tika 解析文档内容
3. 调用智谱 API 生成 Embedding
4. 文档切分后存入 SimpleVectorStore

### 5. 开始问答

```bash
# 带上下文的 RAG 问答（使用 Top-K 检索）
curl "http://localhost:8080/rag/ask?question=你的问题"

# 简单 RAG 问答（取前 3 条）
curl "http://localhost:8080/rag/ask1?question=你的问题"
```

## API 接口

### `GET /rag/ask` — RAG 问答（带 Top-K 搜索）

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | string | 是 | 用户问题 |

**响应示例：**

```json
{
  "answer": "根据资料显示，答案是...",
  "sources": [
    {
      "fileName": "产品手册.docx",
      "source": "file [产品手册.docx]",
      "content": "相关原文段落..."
    }
  ]
}
```

### `GET /rag/ask1` — 简单 RAG 问答

参数同上，固定取前 3 条相似文档。

## 项目结构

```
src/main/java/com/example/rag/
├── Application.java          # Spring Boot 启动入口
├── AiConfig.java             # Bean 配置（Embedding Model、VectorStore）
├── ZhipuConfig.java          # 配置属性扫描
├── IngestionService.java     # 启动时自动导入知识文档
├── RagController.java        # RAG 问答 REST API
├── RagProperties.java        # RAG 配置属性（docsPath, topK）
├── ZhipuProperties.java      # 智谱 API 配置属性
├── ZhipuChatClient.java      # 智谱 Chat API 调用（结构化返回）
├── ZhipuChatService.java     # 智谱 Chat API 调用（通用实现）
├── ZhipuEmbeddingModel.java  # 自定义 Embedding Model（接入智谱）
├── RagResponse.java          # RAG 响应 DTO
└── SourceChunk.java          # 向量数据 DTO
```

## 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `app.zhipu.api-key` | `${ZHIPU_API_KEY}` | 智谱 API Key |
| `app.zhipu.chat-model` | `glm-4-flash` | 对话模型 |
| `app.zhipu.embedding-model` | `embedding-3` | 向量化模型 |
| `app.zhipu.chat-url` | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | Chat API 地址 |
| `app.zhipu.embedding-url` | `https://open.bigmodel.cn/api/paas/v4/embeddings` | Embedding API 地址 |
| `app.rag.docs-path` | `classpath:/docs/*.docx` | 文档路径（Ant 风格） |
| `app.rag.top-k` | `4` | 检索返回的文档数量 |

## 工作原理

```
┌─────────────────────────────────────────────────────┐
│                  启动时 (IngestionService)           │
│                                                     │
│  docs/*.docx ──► TikaDocumentReader ──► TokenTextSplitter ──► VectorStore ──► 内存向量库
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                                                              │
┌─────────────────────────────────────────────────────┐                      │
│                 问答时 (RagController)               │                      │
│                                                     │                      │
│  问题 ──► VectorStore.similaritySearch() ───────────┘                      │
│              │                                                             │
│              ▼                                                             │
│     返回相似文档 ──► 拼接 Prompt ──► ZhipuChatClient.chat() ──► 答案      │
│                                                     │                      │
│              + 来源信息返回给用户                                           │
└─────────────────────────────────────────────────────┘
```

## 常见问题

### Q: 启动时提示 "No files found in ..."
A: 检查 `src/main/resources/docs/` 下是否有匹配的文件，或修改 `app.rag.docs-path` 配置。

### Q: 调用智谱 API 超时或失败
A: 确保 `ZHIPU_API_KEY` 环境变量已正确设置，且账户有足够的额度。

### Q: 如何切换为其他大模型？
A: 替换 `ZhipuChatClient` 和 `ZhipuEmbeddingModel` 为对应厂商的实现即可。

## License

MIT
