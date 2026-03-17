# RAG Implementation Strategy for Spring Boot

This document outlines a structured approach for implementing Retrieval-Augmented Generation (RAG) in this project, focusing on session-based context management and persistent chat history.

## 1. Core Architecture Components

### A. Spring AI Integration
Leverage the **Spring AI** framework, which provides a clean abstraction for interacting with various AI models and vector databases.

### B. Chat Context & Memory
Managing conversation flow requires keeping track of previous messages within a session.

1.  **Session-Level (Ephemeral):**
    *   **Tool:** `ChatMemory` (Spring AI interface).
    *   **Mechanism:** Stores recent message exchanges in-memory or in a fast cache (like Redis) during an active user session.
    *   **Strategy:** Implement a "Windowing" approach where only the last $N$ tokens/messages are sent back to the LLM to keep context relevant and manage costs.

2.  **Persistent (Long-term):**
    *   **Tool:** JPA + MySQL (already in project).
    *   **Mechanism:** A `ChatHistory` entity to store every message with `sessionId`, `role` (user/assistant), `content`, and `timestamp`.
    *   **Benefit:** Allows users to resume conversations across different sessions or devices.

## 2. RAG Workflow & Data Structure

### A. Document Ingestion (The "R" in RAG)
1.  **ETL Pipeline:** Extract content (PDF, Text, DB records), Split into chunks, and Generate Embeddings.
2.  **Vector Store:** Since the project uses MySQL, you can use:
    *   **Simple:** A specialized Vector DB (Pinecone, Weaviate).
    *   **Integrated:** `PGVector` (if moving to Postgres) or a standalone service like `ChromaDB`.

### B. Retrieval & Augmentation
1.  **Query Expansion:** The user's query is converted into an embedding.
2.  **Similarity Search:** Search the Vector Store for the top $K$ most relevant document chunks.
3.  **Prompt Augmentation:** Combine the retrieved chunks + the current `ChatMemory` + the user's query into a single prompt for the LLM.

## 3. Suggested Package Structure

```text
src/main/java/dev/pradeep/dockerbackend/
├── ai/
│   ├── config/              # Spring AI & Model configurations
│   ├── controller/          # Chat & RAG endpoints
│   ├── service/             # Orchestration: RAG + Memory + LLM call
│   ├── repository/          # Vector Store abstraction & JPA Chat History
│   └── model/               # ChatEntity, DocumentChunk, etc.
└── rag/
    ├── document/            # Document loaders and splitters
    └── embedding/           # Custom embedding logic (if any)
```

## 4. Implementation Steps (Phased)

1.  **Phase 1: Basic Chat + Persistence**
    *   Add Spring AI dependencies.
    *   Implement JPA entities for Chat History.
    *   Create a simple REST endpoint that echoes responses but saves them to MySQL.

2.  **Phase 2: Context Awareness**
    *   Integrate `ChatMemory` to keep track of the current session's thread.
    *   Update the prompt to include previous context.

3.  **Phase 3: Full RAG**
    *   Set up a Vector Store (e.g., ChromaDB via Docker).
    *   Implement a service to ingest project-specific data into the Vector Store.
    *   Modify the chat service to perform a "Search -> Augment -> Generate" flow.

## 6. Deep Dive: Embedding Mechanics & Data Pipeline

### A. How Embeddings Work
Embeddings transform unstructured text into mathematical vectors (arrays of floating-point numbers).
1.  **Numerical Representation:** An LLM embedding model takes a string (e.g., "How to reset a password") and outputs a vector (e.g., `[0.012, -0.045, ... 1536 dims]`).
2.  **Semantic Proximity:** Sentences that are conceptually similar will have vectors that point in nearly the same direction.
3.  **Similarity Search:** We use "Cosine Similarity" to find which stored vectors are most similar to the user's query vector.

### B. Prompt Structure for LLM Context
When sending the final request to the LLM, the "Context" is injected into the System Prompt.

**Suggested Prompt Template:**
```text
SYSTEM: You are a helpful assistant. Use the provided Context and Chat History to answer the User Question. If the answer isn't in the context, say you don't know.

CONTEXT:
---
[Retrieved Chunk 1 from Vector Store]
[Retrieved Chunk 2 from Vector Store]
---

CHAT HISTORY:
User: [Previous Message]
Assistant: [Previous Response]

USER QUESTION: {current_user_query}
```

### C. Converting MySQL Data to Embeddings (Pipeline)
Since your project uses MySQL, here is how you move data from your relational tables into the RAG system:

1.  **The Fetcher:** Use a JPA Repository to fetch your target records (e.g., `productRepository.findAll()`).
2.  **The Transformer:** Concatenate relevant fields into a "Document" string.
    *   *Example:* `String content = "Product: " + p.getName() + " | Description: " + p.getDesc();`
3.  **The Embedder:** Use Spring AI's `EmbeddingClient` to convert that string into a vector.
    *   `float[] vector = embeddingClient.embed(content);`
4.  **The Vector Store Load:** Save this vector along with a reference ID to the original MySQL record into your Vector Store (e.g., ChromaDB).

**Code Workflow Concept:**
```java
// 1. Fetch from MySQL
List<Product> products = repo.findAll();

// 2. Convert to Spring AI Documents
List<Document> documents = products.stream()
    .map(p -> new Document(p.getId().toString(), p.toContentString(), Map.of("category", p.getCategory())))
    .toList();

// 3. Save to Vector Store (this handles embedding internally)
vectorStore.add(documents);
```
