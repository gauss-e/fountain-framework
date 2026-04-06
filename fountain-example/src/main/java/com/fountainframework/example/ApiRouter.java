package com.fountainframework.example;

import com.fountainframework.core.router.FountainRouter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom meta-annotation — carries @FountainRouter.
 * Any class annotated with @ApiRouter will be auto-discovered as a router configurer.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@FountainRouter
public @interface ApiRouter {
}
