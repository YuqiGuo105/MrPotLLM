# Mr Pot

## Swagger UI

Once the application is running, the interactive API documentation is available at:

- Swagger UI: http://localhost:8080/swagger-ui
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Build and test
- Build without running tests: `mvn clean install -DskipTest`
- Run the full test suite: `mvn test`

## SSE streaming endpoint
You can stream answers with Server-Sent Events using:

```
curl -N \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -X POST \
  -d '{"question": "Hi ....", "sessionId": "001"}' \
  http://localhost:8080/api/rag/answer/stream
```
