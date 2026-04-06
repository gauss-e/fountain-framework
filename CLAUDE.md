# Fountain Framework — CLAUDE.md

## Project Overview

Fountain is a lightweight Java HTTP framework built on [Netty](https://netty.io/), inspired by Go's [Gin](https://github.com/gin-gonic/gin). It aims for zero-reflection dispatch, virtual-thread concurrency, and a clean functional handler API.

**Requirements:** Java 25+, Maven 3.9+

## Module Structure

```
fountain-framework/
├── fountain-common/     # Shared utilities (GaussException)
├── fountain-core/       # Framework core: server, routing, handlers, scanning
│   └── src/main/java/com/fountainframework/core/
│       ├── Fountain.java                  # Bootstrap entry point
│       ├── FountainApplication.java       # App lifecycle (router + server wiring)
│       ├── handler/
│       │   ├── FountainContext.java        # Request context passed to every handler
│       │   ├── FountainHandler.java        # Typed body handler: (R body, ctx) -> O
│       │   ├── ContextHandler.java         # Context-only handler: ctx -> O
│       │   ├── RouteHandler.java           # Unified internal handler (post-adaptation)
│       │   └── HandlerAdapter.java         # Adapts handlers at registration time
│       ├── router/
│       │   ├── Router.java                 # Gin-style router with path params & groups
│       │   ├── RouterConfigurer.java       # Interface implemented by @FountainRouter classes
│       │   ├── FountainRouter.java         # Annotation (supports meta-annotation)
│       │   ├── RouteEntry.java             # Route metadata (method, segments, handler)
│       │   └── FountainRouter.java         # @FountainRouter annotation
│       ├── scanner/
│       │   └── ClassScanner.java           # ASM bytecode scanner (no Class.forName during scan)
│       ├── server/
│       │   ├── FountainServer.java         # Netty NIO server + virtual thread executor
│       │   ├── FountainHttpHandler.java    # Netty channel handler — decodes requests, dispatches
│       │   └── FountainChannelInitializer.java
│       ├── http/
│       │   ├── HttpResponse.java           # Response builder with factory methods
│       │   ├── FountainPoolRequest.java    # Decoded request (path, headers, body, query params)
│       │   ├── HttpHeaders.java
│       │   ├── HttpMethod.java
│       │   └── HttpVersion.java
│       └── serialize/
│           ├── BodyReader.java             # Interface: byte[] -> T
│           ├── JacksonBodyReader.java      # Jackson implementation
│           ├── ResponseWriter.java         # Interface: Object -> HttpResponse
│           └── JacksonResponseWriter.java  # POJO->JSON, String->text/plain, HttpResponse passthrough
└── fountain-example/    # Working example app
```

## Core Concepts

### Bootstrap
```java
Fountain.run(MyApp.class).start(8080);
```
`Fountain.run()` uses `ClassScanner` (ASM) to find all `@FountainRouter` classes in the app's package without loading them via reflection. It then instantiates them, calls `configure(router)`, and starts the Netty server.

### Defining Routes
Implement `RouterConfigurer` and annotate with `@FountainRouter`:
```java
@FountainRouter
public class MyRoutes implements RouterConfigurer {
    @Override
    public void configure(Router router) {
        // Context-only handler
        router.get("/hello", ctx -> HttpResponse.ok("Hello!"));

        // Path parameters
        router.get("/users/:id", ctx -> {
            long id = ctx.pathParamAsLong("id");
            return HttpResponse.json("{\"id\":" + id + "}");
        });

        // Typed body handler — auto-deserialized from JSON
        router.post("/users", User.class, (user, ctx) -> {
            return new HttpResponse(201).body(mapper.writeValueAsString(user))
                    .contentType("application/json; charset=UTF-8");
        });

        // Route groups
        router.group("/api/v1", api -> {
            api.get("/status", c -> HttpResponse.json("{\"status\":\"ok\"}"));
        });
    }
}
```

### Handler Types
| Interface | Signature | Use when |
|---|---|---|
| `ContextHandler<O>` | `ctx -> O` | No request body needed |
| `FountainHandler<R, O>` | `(R body, ctx) -> O` | Need typed JSON body |

Both can return any type `O`. `ResponseWriter` auto-converts:
- `HttpResponse` → passed through unchanged
- `String` → `text/plain; charset=UTF-8`
- Any POJO → `application/json; charset=UTF-8` (Jackson)

### FountainContext API
```java
ctx.pathParam("name")          // String path param
ctx.pathParamAsLong("id")      // parsed long
ctx.pathParamAsInt("id")       // parsed int
ctx.queryParam("q")            // query string param
ctx.queryParam("q", "default") // with default
ctx.header("Authorization")    // request header
ctx.body()                     // raw byte[]
ctx.bodyAsString()             // UTF-8 string
ctx.method()                   // HttpMethod enum
ctx.path()                     // /users/42
ctx.uri()                      // /users/42?foo=bar
```

### HttpResponse Factory Methods
```java
HttpResponse.ok()              // 200, no body
HttpResponse.ok("text")        // 200, text/plain
HttpResponse.json("{...}")     // 200, application/json
HttpResponse.notFound()        // 404
HttpResponse.badRequest()      // 400
HttpResponse.error("msg")      // 500
new HttpResponse(201).body("...").contentType("application/json; charset=UTF-8")
```

## Key Design Decisions

- **ASM scanning over reflection**: `ClassScanner` reads `.class` bytecode with ASM to detect `@FountainRouter` without triggering class loading. Meta-annotations are supported (an annotation annotated with `@FountainRouter` is also recognized).
- **Zero per-request type resolution**: `HandlerAdapter` composes the `deserialize → handle → serialize` pipeline once at route registration. `RouteHandler` is a simple `ctx -> HttpResponse` function at dispatch time.
- **Virtual threads**: Every handler invocation is dispatched to a `newVirtualThreadPerTaskExecutor`. A `Semaphore(1000)` caps concurrency.
- **PECS generics**: `FountainHandler<? super R, ?>` allows a handler for a supertype to be registered against a subtype class token.
- **Inner class support**: `ClassScanner` processes `Outer$Inner.class` files, so `@FountainRouter` inner classes work.

## Build

```bash
# Build all modules
mvn clean install

# Build specific module
mvn clean install -pl fountain-core

# Run example
mvn exec:java -pl fountain-example
```

## Extending the Framework

- **New serialization format**: Implement `BodyReader` and `ResponseWriter`, wire into `HandlerAdapter` via `FountainApplication`.
- **Middleware / filters**: Currently no middleware chain — would need to be added to `Router` or `FountainHttpHandler`.
- **Async handlers**: Virtual threads handle blocking I/O well; for reactive patterns the Netty pipeline would need extension.
- **JAR scanning**: `ClassScanner` currently only supports `file://` classpath entries. JAR support would require handling `jar://` URLs.
