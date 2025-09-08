package com.stocksanalyses.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int value() default 100; // requests per minute
    int windowMinutes() default 1;
    String key() default ""; // custom key for rate limiting
}
