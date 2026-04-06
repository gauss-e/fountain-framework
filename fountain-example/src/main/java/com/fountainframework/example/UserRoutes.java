package com.fountainframework.example;

import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.router.FountainRouter;
import com.fountainframework.core.router.Router;
import com.fountainframework.core.router.RouterConfigurer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example router configurer — auto-discovered by {@code Fountain.run()}.
 *
 * <pre>
 *   GET    /                    → welcome message
 *   GET    /hello               → Hello, Fountain!
 *   GET    /hello?name=World    → Hello, World!
 *   GET    /users               → list all users
 *   POST   /users               → create user (JSON body)
 *   GET    /users/:id           → get user by id
 *   DELETE /users/:id           → delete user by id
 * </pre>
 */
@FountainRouter
public class UserRoutes implements RouterConfigurer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @Override
    public void configure(Router router) {

        router.get("/", ctx ->
                HttpResponse.json("{\"message\":\"Welcome to Fountain Framework\",\"version\":\"1.0.0\"}"));

        router.get("/hello", ctx -> {
            String name = ctx.queryParam("name", "Fountain");
            return HttpResponse.ok("Hello, " + name + "!");
        });

        router.get("/users", ctx -> {
            List<User> list = List.copyOf(users.values());
            return HttpResponse.json(mapper.writeValueAsString(list));
        });

        router.get("/users/:id", ctx -> {
            long id = ctx.pathParamAsLong("id");
            User user = users.get(id);
            if (user == null) {
                return HttpResponse.notFound();
            }
            return HttpResponse.json(mapper.writeValueAsString(user));
        });

        router.post("/users", ctx -> {
            User input = ctx.bodyAs(User.class);
            long id = idGen.getAndIncrement();
            User saved = new User(id, input.name(), input.email());
            users.put(id, saved);
            return new HttpResponse(201)
                    .contentType("application/json; charset=UTF-8")
                    .body(mapper.writeValueAsString(saved));
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
