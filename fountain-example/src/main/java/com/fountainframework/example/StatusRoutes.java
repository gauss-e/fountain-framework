package com.fountainframework.example;

import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.router.Router;
import com.fountainframework.core.router.RouterConfigurer;

/**
 * Demonstrates meta-annotation discovery:
 * This class uses the custom @ApiRouter (which carries @FountainRouter)
 * and is still auto-discovered by the ASM scanner.
 */
@ApiRouter
public class StatusRoutes implements RouterConfigurer {

    @Override
    public void configure(Router router) {
        router.group("/api/v1", api ->
                api.get("/status", ctx ->
                        HttpResponse.json("{\"status\":\"ok\"}")));
    }
}
