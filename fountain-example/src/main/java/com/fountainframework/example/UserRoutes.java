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
 * Example router demonstrating handler styles:
 * <ul>
 *   <li>{@code ContextHandler<O>} — receives FountainEntry with path/query params</li>
 *   <li>{@code SimpleHandler<O>} — no input parameters</li>
 *   <li>{@code FountainHandler<R, O>} — typed body auto-deserialization (POST/PUT)</li>
 * </ul>
 */
@FountainRouter
public class UserRoutes implements RouterConfigurer {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @Override
    public void configure(Router router) {

        // ContextHandler<O> — FountainEntry passed directly, no boilerplate

        router.get("/", entry ->
                Map.of("message", "Welcome to Fountain Framework", "version", "1.0.0"))
            .get("/hello", entry -> {
                String name = entry.queryParam("name", "Fountain");
                return HttpResponse.ok("Hello, " + name + "!");
            })
            .get("/users", entry -> List.copyOf(users.values()))
            .get("/users/{id}", entry -> {
                long id = entry.pathParamAsLong("id");
                User user = users.get(id);
                if (user == null) {
                    return HttpResponse.notFound();
                }
                return user;  // auto-serialized to JSON
            })

            // FountainHandler<R, O> — typed body auto-deserialized
            .post("/users", User.class, user -> {
                long id = idGen.getAndIncrement();
                User saved = new User(id, user.name(), user.email());
                users.put(id, saved);
                return saved;  // auto-serialized to JSON
            })

            // ContextHandler<O> — DELETE with path param
            .delete("/users/{id}", entry -> {
                long id = entry.pathParamAsLong("id");
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
