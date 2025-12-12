# Mr Pot

## Run locally from the command line
1. Build the application (skipping tests if you only want a runnable jar):
   ```bash
   ./mvnw clean package -DskipTests
   ```
2. Start the jar with the `dev` profile so the container-friendly defaults in `application-dev.yml` are applied:
   ```bash
   SPRING_PROFILES_ACTIVE=dev \
   PGHOST=localhost PGPORT=5432 PGDATABASE=mrpot PGUSER=postgres PGPASSWORD=postgres \
   REDIS_HOST=localhost REDIS_PORT=6379 REDIS_PASSWORD=redis \
   java -jar target/MrPot-0.0.1-SNAPSHOT.jar
   ```

## Docker
Build and run the containerized application (defaults to the `dev` profile inside the image):
```bash
docker build -t mrpot:latest .

docker run --rm -p 8080:8080 \
  -e PGHOST=postgres -e PGPORT=5432 -e PGDATABASE=mrpot \
  -e PGUSER=postgres -e PGPASSWORD=postgres \
  -e REDIS_HOST=redis -e REDIS_PORT=6379 -e REDIS_PASSWORD=redis \
  -e SPRING_PROFILES_ACTIVE=dev \
  mrpot:latest
```

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
