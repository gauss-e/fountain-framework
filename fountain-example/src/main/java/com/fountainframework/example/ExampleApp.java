package com.fountainframework.example;

import com.fountainframework.core.FountainApplication;
import com.fountainframework.core.annotation.*;
import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.HttpResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example application demonstrating Fountain framework features.
 * Start it and try:
 *   GET  http://localhost:8080/hello
 *   GET  http://localhost:8080/hello?name=World
 *   GET  http://localhost:8080/users
 *   POST http://localhost:8080/users        (JSON body: {"name":"Alice","email":"alice@example.com"})
 *   GET  http://localhost:8080/users/1
 *   DELETE http://localhost:8080/users/1
 */
public class ExampleApp {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        FountainApplication.create()
                .register(new HelloController(), new UserController())
                .start(port);
    }

    // --- Hello Controller ---

    public static class HelloController {

        @Get("/hello")
        public HttpResponse hello(FountainPoolRequest request) {
            String name = request.queryParameter("name");
            if (name == null) {
                name = "Fountain";
            }
            return HttpResponse.ok("Hello, " + name + "!");
        }

        @Get("/")
        public HttpResponse index() {
            return HttpResponse.json("""
                    {"message":"Welcome to Fountain Framework","version":"1.0.0"}""");
        }
    }

    // --- User Controller with CRUD ---

    public static class UserController {

        private final Map<Long, User> users = new ConcurrentHashMap<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Get("/users")
        public List<User> listUsers() {
            return List.copyOf(users.values());
        }

        @Get("/users/{id}")
        public HttpResponse getUser(long id) {
            User user = users.get(id);
            if (user == null) {
                return HttpResponse.notFound();
            }
            return HttpResponse.json(toJson(user));
        }

        @Post("/users")
        public HttpResponse createUser(@RequestBody User user) {
            long id = idGenerator.getAndIncrement();
            User saved = new User(id, user.name(), user.email());
            users.put(id, saved);
            return new HttpResponse(201)
                    .contentType("application/json; charset=UTF-8")
                    .body(toJson(saved));
        }

        @Delete("/users/{id}")
        public HttpResponse deleteUser(long id) {
            User removed = users.remove(id);
            if (removed == null) {
                return HttpResponse.notFound();
            }
            return HttpResponse.ok("Deleted user " + id);
        }

        private String toJson(User user) {
            return """
                    {"id":%d,"name":"%s","email":"%s"}""".formatted(user.id(), user.name(), user.email());
        }
    }

    public record User(long id, String name, String email) {
        public User() {
            this(0, "", "");
        }
    }
}
