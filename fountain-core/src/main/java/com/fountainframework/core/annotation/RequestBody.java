package com.fountainframework.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter to be deserialized from the HTTP request body.
 *
 * <p>The framework will automatically deserialize the request body (JSON)
 * into the annotated parameter's type using Jackson.</p>
 *
 * <pre>{@code
 * @Post("/users")
 * public HttpResponse createUser(@RequestBody User user) {
 *     return HttpResponse.ok("Created: " + user.name());
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestBody {
}
