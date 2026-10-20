# AutoRESTifier

A local-first Java developer tool prototype: upload a compiled `.jar`, inspect its class/method metadata, explicitly expose eligible **public static methods**, then invoke them through generated HTTP endpoints from the browser.

## Requirements
- JDK 25
- Maven 3.9+
- IntelliJ IDEA Community Edition (optional)

## Run
From the project root:

```bash
mvn clean compile exec:java
```

Open <http://127.0.0.1:7070>.

The first run downloads Maven dependencies, so it needs internet unless the dependencies are already cached.

## Quick demo
1. Build the included sample library:
   ```bash
   cd sample-library
   mvn clean package
   ```
2. In the AutoRESTifier browser, upload `sample-library/target/calculator-sample-1.0.jar`.
3. Select `demo.Calculator` and its `add(int, int)` method.
4. Click **Expose method**.
5. Use the generated endpoint form or call it directly:
   ```bash
   curl -X POST http://127.0.0.1:7070/generated/<endpoint-id> ^
     -H "Content-Type: application/json" ^
     -d "{\"args\":[12,8]}"
   ```
   On PowerShell or CMD, replace `<endpoint-id>` with the ID shown by the UI (do not include angle brackets).

## What this prototype supports
- JAR upload and bytecode metadata scanning using ASM
- Public static method discovery
- Explicit method exposure
- JSON arguments and JSON results for supported Java primitive/wrapper types, `String`, and `void`
- Dynamic POST endpoint registration through a shared generated-route handler
- Browser-based endpoint testing
- Local-only binding to `127.0.0.1`

## Important limitations
- **Only upload JARs you trust.** Exposing a method executes code from that JAR in this JVM. Java class loading is not a security sandbox.
- This prototype intentionally exposes only public static methods. It does not instantiate arbitrary classes.
- Methods with unsupported argument types or return types may be discoverable but will fail validation at invocation time.
- Uploaded JAR dependencies are not automatically resolved. The JAR should be self-contained or use classes already on the application's classpath.
- The prototype keeps the current upload and exposed endpoint registry in memory; restarting clears it.
- No authentication is included because the server binds to loopback. Do not change it to a public interface without adding authentication, authorization, resource limits, and process isolation.

## Project layout
- `src/main/java/dev/autorestifier/AutoRestifierApplication.java` — HTTP server and API routes
- `src/main/java/dev/autorestifier/JarInspector.java` — ASM metadata scanner
- `src/main/java/dev/autorestifier/EndpointRegistry.java` — exposed endpoint registry and reflective invocation
- `src/main/resources/public/` — browser studio
- `sample-library/` — tiny example JAR to build and upload

## API endpoints
- `GET /api/status`
- `POST /api/upload` (multipart field name `jar`)
- `GET /api/classes`
- `POST /api/expose` JSON: `{"className":"demo.Calculator","methodName":"add","descriptor":"(II)I"}`
- `GET /api/endpoints`
- `POST /generated/{id}` JSON: `{"args":[12,8]}`

This is an MVP foundation, not a production-ready arbitrary-code hosting platform.
