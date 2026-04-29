package com.golivebackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for REST endpoints.
 *
 * WHY WebMvcConfigurer AND NOT @CrossOrigin?
 * @CrossOrigin can be placed on individual controllers or methods.
 * That approach scatters CORS policy across the codebase — different
 * controllers may have different (possibly conflicting) settings.
 *
 * WebMvcConfigurer defines CORS policy in ONE place for ALL endpoints.
 * When policy changes (new frontend domain in production), you change
 * one file — not every controller.
 *
 * WHY NOT A CorsFilter Bean?
 * CorsFilter is lower-level — it intercepts at the servlet filter chain.
 * For standard Spring MVC applications, WebMvcConfigurer is the correct
 * level of abstraction. CorsFilter is needed when you have non-MVC
 * endpoints or use Spring Security (which we don't, yet).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /*
     * Allowed origins injected from configuration.
     * This means we never hardcode frontend URLs in Java code.
     *
     * In application-local.yml:  http://localhost:3000
     * In application-prod.yml:   ${FRONTEND_URL} (env var)
     *
     * We'll add these to the yml files after this class.
     *
     * String[] because allowedOrigins() takes varargs —
     * multiple origins can be allowed simultaneously.
     */
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                /*
                 * Apply this CORS policy to ALL paths under /api.
                 * /** means recursive — covers /api/streams,
                 * /api/streams/123, /api/livekit/token, etc.
                 */
                .addMapping("/**")

                /*
                 * Which frontend origins are allowed.
                 * Comes from application.yml — different per environment.
                 *
                 * NEVER use allowedOrigins("*") in combination with
                 * allowCredentials(true) — browsers reject this combination
                 * and it's a security risk regardless.
                 */
                .allowedOrigins(allowedOrigins)

                /*
                 * HTTP methods the frontend is allowed to use.
                 * We list exactly what our API uses — not a blanket *.
                 * DELETE is included for future stream deletion if needed.
                 */
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")

                /*
                 * Which request headers the frontend can send.
                 * Content-Type: needed for POST/PATCH with JSON body
                 * Authorization: included for future auth headers
                 * X-Host-Key: we'll use this custom header to pass
                 *             the hostKey on lifecycle endpoints —
                 *             cleaner than putting secrets in the URL
                 *
                 * allowedHeaders("*") is acceptable here since we control
                 * what headers our frontend sends.
                 */
                .allowedHeaders(
                        "Content-Type",
                        "Authorization",
                        "X-Host-Key"
                )

                /*
                 * Which response headers the frontend JavaScript can read.
                 * By default, browsers only expose a small set of headers
                 * to JavaScript. If your API sets custom response headers
                 * that the frontend needs to read, expose them here.
                 */
                .exposedHeaders("Content-Type")

                /*
                 * How long (seconds) the browser caches the preflight response.
                 * 3600 = 1 hour. Browser won't send OPTIONS preflight again
                 * for the same endpoint within this window.
                 * Reduces preflight round-trips in production.
                 */
                .maxAge(3600);
    }
}