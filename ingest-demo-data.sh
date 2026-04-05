#!/bin/bash

echo "Starting ingestion of demo data into the Advanced RAG vector store..."

# Ensure the app is running before firing the curl request
curl -X POST http://localhost:8080/api/rag/ingest \
  -H "Content-Type: application/json" \
  -d @demo-data.json

echo ""
echo "Ingestion request completed! Try hitting the /api/rag/search endpoint now."
