# Fountain Framework

A lightweight, high-performance HTTP framework for Java, built on [Netty](https://netty.io/).

Fountain takes inspiration from Go's [Gin](https://github.com/gin-gonic/gin) — define routes with functional handlers, zero reflection at dispatch time, and virtual threads by default.

## Features

- **Zero reflection at dispatch time** — routes map directly to lambda/method-reference handlers, no `Method.invoke()` at runtime
- **Gin-style routing** — `router.get("/users/:id", handler)` with path parameters, query parameters, and route groups
- **Virtual threads** — handler execution runs on virtual threads (default 1000 concurrency), freeing Netty I/O threads for maximum throughput
- **Spring Boot-like startup** — `Fountain.run(MyApp.class).start(8080)` auto-discovers `@FountainRouter` classes
- **Functional handlers** — implement `FountainHandler` as a lambda: context in, response out
- **JSON support** — built-in Jackson integration via `ctx.bodyAs(MyClass.class)`
- **Built on Netty** — non-blocking I/O, high throughput, low latency
- **Lightweight** — minimal dependencies, small API surface, fast startup

## Quick Start

### 1. Define your application entry point

```java
import com.fountainframework.core.Fountain;

public class MyApp {
    public static void main(String[] args) {
        Fountain.run(MyApp.class).start(8080);
    }
}
```

### 2. Define routes with `@FountainRouter`

```java
import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.router.*;

@FountainRouter
public class MyRoutes implements RouterConfigurer {

    @Override
    public void configure(Router router) {
        router.get("/hello", ctx -> HttpResponse.ok("Hello, World!"));

        router.get("/hello/:name", ctx -> {
            String name = ctx.pathParam("name");
            return HttpResponse.ok("Hello, " + name + "!");
        });
    }
}
```

That's it. `Fountain.run()` scans the package of your app class, finds all `@FountainRouter` classes, and registers their routes automatically.

## Routing

### Path Parameters

Use `:param` syntax (Gin-style) to capture path segments:

```java
router.get("/users/:id", ctx -> {
    long id = ctx.pathParamAsLong("id");
    return HttpResponse.ok("User ID: " + id);
});

router.get("/files/:category/:name", ctx -> {
    String category = ctx.pathParam("category");
    String name = ctx.pathParam("name");
    return HttpResponse.ok(category + "/" + name);
});
```

### Query Parameters

```java
router.get("/search", ctx -> {
    String q = ctx.queryParam("q", "");           // with default
    String page = ctx.queryParam("page", "1");
    return HttpResponse.ok("Search: " + q + ", page: " + page);
});
```

### Request Body (JSON)

```java
public record CreateUserRequest(String name, String email) {}

router.post("/users", ctx -> {
    CreateUserRequest req = ctx.bodyAs(CreateUserRequest.class);
    // ... save user
    return new HttpResponse(201)
            .contentType("application/json; charset=UTF-8")
            .body("{\"name\":\"" + req.name() + "\"}");
});
```

### Route Groups

```java
router.group("/api/v1", api -> {
    api.get("/users", ctx -> { /* ... */ });
    api.post("/users", ctx -> { /* ... */ });
    api.get("/users/:id", ctx -> { /* ... */ });
});
```

### All HTTP Methods

```java
router.get("/resource",    ctx -> { /* ... */ });
router.post("/resource",   ctx -> { /* ... */ });
router.put("/resource",    ctx -> { /* ... */ });
router.delete("/resource", ctx -> { /* ... */ });
router.patch("/resource",  ctx -> { /* ... */ });
router.head("/resource",   ctx -> { /* ... */ });
router.options("/resource", ctx -> { /* ... */ });
router.any("/resource",    ctx -> { /* ... */ });  // all methods
```

### Inline Mode (without `@FountainRouter`)

You can also register routes directly on the application object:

```java
FountainApplication app = FountainApplication.create();
app.get("/hello", ctx -> HttpResponse.ok("Hello!"));
app.start(8080);
```

## FountainContext API

Every handler receives a `FountainContext` with these methods:

| Method | Description |
|--------|-------------|
| `pathParam(name)` | Get path parameter as String |
| `pathParamAsInt(name)` | Get path parameter as int |
| `pathParamAsLong(name)` | Get path parameter as long |
| `queryParam(name)` | Get query parameter |
| `queryParam(name, default)` | Get query parameter with default |
| `queryParams(name)` | Get all values for a query parameter |
| `header(name)` | Get request header |
| `headers()` | Get all headers |
| `body()` | Get raw body bytes |
| `bodyAsString()` | Get body as UTF-8 string |
| `bodyAs(Class)` | Deserialize JSON body to object |
| `method()` | HTTP method |
| `path()` | Request path |
| `uri()` | Full URI with query string |
| `remoteAddress()` | Client IP address |
| `request()` | Access the underlying raw request |

## HttpResponse API

Build responses with the fluent API:

```java
HttpResponse.ok("text body")                    // 200 text/plain
HttpResponse.json("{\"key\":\"value\"}")         // 200 application/json
HttpResponse.notFound()                          // 404
HttpResponse.badRequest()                        // 400
HttpResponse.error("message")                    // 500

// Custom response
new HttpResponse(201)
    .contentType("application/json; charset=UTF-8")
    .header("X-Custom", "value")
    .body("{\"created\":true}");
```

## Architecture

```
fountain-framework/
├── fountain-common/     # Shared utilities (GaussException)
├── fountain-core/       # Core framework
│   ├── Fountain.java    # Bootstrap: Fountain.run(Class) entry point
│   ├── handler/         # FountainHandler (functional interface) + FountainContext
│   ├── router/          # Router, RouteEntry, @FountainRouter, RouterConfigurer
│   ├── http/            # HttpRequest, HttpResponse, HttpHeaders, HttpMethod, HttpVersion
│   └── server/          # Netty server with virtual thread dispatch
└── fountain-example/    # Example application
```

### Performance Design

- **No reflection at dispatch time** — handlers are direct function references (lambdas / method references). Reflection is only used once at startup to discover `@FountainRouter` classes.
- **Virtual threads** — handler business logic runs on virtual threads (default 1000 concurrency), keeping Netty I/O event loop threads free for socket operations.
- **Pre-compiled routes** — path patterns are parsed once at startup into segment arrays. Matching is a simple array comparison — no regex at runtime.
- **EnumMap dispatch** — routes are indexed by `HttpMethod` enum for O(1) method lookup before path matching.
- **Netty NIO** — non-blocking event loop handles thousands of concurrent connections with minimal threads.

### Request Flow

```
Client → Netty I/O Thread (parse HTTP) → Virtual Thread (run handler) → Netty I/O Thread (write response)
```

## Requirements

- Java 21+ (virtual threads)
- Maven 3.9+

## Build

```bash
mvn clean compile
```

## Run Example

```bash
mvn clean compile
java -cp "fountain-example/target/classes:fountain-core/target/classes:fountain-common/target/classes:<dependencies>" \
  com.fountainframework.example.ExampleApp
```

Then test:

```bash
curl http://localhost:8080/hello
curl http://localhost:8080/hello?name=World
curl -X POST http://localhost:8080/users -H "Content-Type: application/json" -d '{"name":"Alice","email":"alice@example.com"}'
curl http://localhost:8080/users/1
curl http://localhost:8080/api/v1/status
```

## License

MIT
