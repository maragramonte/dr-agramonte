package com.saludybienestar.agramonte.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Cabeceras de caché para los recursos estáticos servidos por Spring Boot.
 *
 * La auditoría SEO (AIOSEO) avisaba de que las imágenes se servían sin cabecera
 * de expiración. Imágenes e iconos casi nunca cambian, así que se cachean de
 * forma agresiva en el navegador (30 días) para mejorar rendimiento y la nota SEO.
 *
 * A propósito NO se tocan el CSS y el JS: sus nombres no llevan hash de versión y
 * el service worker (cache-first) ya gestiona su actualización; una caché larga
 * aquí podría servir versiones obsoletas tras un despliegue. El HTML también
 * mantiene la política por defecto para que los cambios se publiquen al instante.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Duration ASSET_MAX_AGE = Duration.ofDays(30);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl cacheControl = CacheControl.maxAge(ASSET_MAX_AGE).cachePublic();
        registry.addResourceHandler("/pictures/**")
                .addResourceLocations("classpath:/static/pictures/")
                .setCacheControl(cacheControl);
        registry.addResourceHandler("/icons/**")
                .addResourceLocations("classpath:/static/icons/")
                .setCacheControl(cacheControl);
    }
}
