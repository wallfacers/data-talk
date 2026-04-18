package com.datatalk.domain.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataTalkAction {
    String id();
    Executor executor();
    String description();
    String[] produces() default {};
    boolean requiresConnection() default false;
    int timeoutMs() default 30_000;
    RiskLevel[] riskLevel() default {};
    Category[] category() default {};
}
