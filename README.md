# Fountain Framework

A lightweight, high-performance HTTP framework for Java, built on [Netty](https://netty.io/).

Inspired by Go's [Gin](https://github.com/gin-gonic/gin) — define routes with functional handlers, virtual threads by default, zero reflection at dispatch time.

## Features

- **Gin-style routing** — `router.get("/users/:id", handler)` with path parameters and route groups
- **Virtual threads** — handler execution runs on virtual threads out of the box
- **Spring Boot-like startup** — `Fountain.run(MyApp.class).start(8080)` auto-discovers `@FountainRouter` classes via ASM bytecode scanning
- **Functional handlers** — define routes as lambdas, no controller classes needed
- **JSON support** — `ctx.bodyAs(MyClass.class)` via Jackson

## Quick Start

**1. Entry point**

```java
public class MyApp {
    public static void main(String[] args) {
        Fountain.run(MyApp.class).start(8080);
    }
}
```

**2. Define routes**

```java
@FountainRouter
public class MyRoutes implements RouterConfigurer {

    @Override
    public void configure(Router router) {
        router.get("/hello", ctx ->
                HttpResponse.ok("Hello, World!"));

        router.get("/users/:id", ctx -> {
            long id = ctx.pathParamAsLong("id");
            return HttpResponse.json("{\"id\":" + id + "}");
        });

        router.post("/users", ctx -> {
            User user = ctx.bodyAs(User.class);
            return new HttpResponse(201).body(mapper.writeValueAsString(user))
                    .contentType("application/json; charset=UTF-8");
        });

        router.group("/api/v1", api -> {
            api.get("/status", c -> HttpResponse.json("{\"status\":\"ok\"}"));
        });
    }
}
```

`Fountain.run()` scans the package of your app class for all `@FountainRouter` classes and registers their routes automatically. Supports inner classes and meta-annotations.

## Requirements

- Java 25+
- Maven 3.9+
