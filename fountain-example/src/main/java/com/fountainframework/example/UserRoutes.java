package com.fountainframework.example;

import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.router.FountainRouter;
import com.fountainframework.core.router.Router;
import com.fountainframework.core.router.RouterConfigurer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example router demonstrating both handler styles:
 * <ul>
 *   <li>{@code ContextHandler<O>} — for GET/DELETE routes (context-only)</li>
 *   <li>{@code FountainHandler<R, O>} — for POST/PUT routes (typed body auto-deserialization)</li>
 * </ul>
 */
@FountainRouter
public class UserRoutes implements RouterConfigurer {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @Override
    public void configure(Router router) {

        // ContextHandler<O> — context only, return type auto-serialized

        router.get("/", ctx ->
                Map.of("message", "Welcome to Fountain Framework", "version", "1.0.0"));

        router.get("/hello", ctx -> {
            String name = ctx.queryParam("name", "Fountain");
            return HttpResponse.ok("Hello, " + name + "!");
        });

        // Return POJO list — auto-serialized to JSON by ResponseWriter
        router.get("/users", ctx -> List.copyOf(users.values()));

        router.get("/users/:id", ctx -> {
            long id = ctx.pathParamAsLong("id");
            User user = users.get(id);
            if (user == null) {
                return HttpResponse.notFound();
            }
            return user;  // auto-serialized to JSON
        });

        // FountainHandler<R, O> — typed body: User is auto-deserialized from JSON
        router.post("/users", User.class, (user, ctx) -> {
            long id = idGen.getAndIncrement();
            User saved = new User(id, user.name(), user.email());
            users.put(id, saved);
            return saved;  // auto-serialized to JSON
        });

        router.delete("/users/:id", ctx -> {
            long id = ctx.pathParamAsLong("id");
            User removed = users.remove(id);
            if (removed == null) {
                return HttpResponse.notFound();
            }
            return HttpResponse.ok("Deleted user " + id);
        });
    }

    public record User(long id, String name, String email) {
        public User() {
            this(0, "", "");
        }
    }
}
