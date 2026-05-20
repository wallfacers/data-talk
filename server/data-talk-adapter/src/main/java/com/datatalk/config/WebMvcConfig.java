package com.datatalk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.logging.slow-request-threshold-ms:1000}")
    private long slowRequestThresholdMs;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLogInterceptor(slowRequestThresholdMs))
                .addPathPatterns("/api/**");
    }

    /**
     * Serve the bezel runtime scheduler from the application module's single source
     * ({@code classpath:/dashboard/renderers/scheduler.js}) at {@code /bezel/scheduler.js},
     * which compiled dashboards reference via an external script tag (CSP-safe, no inline JS).
     * echarts.min.js and geo/ live under {@code static/bezel/} and are served by the default handler.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/bezel/**")
                .addResourceLocations("classpath:/static/bezel/", "classpath:/dashboard/renderers/");
    }
}
