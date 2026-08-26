package com.tienda.pos.catalog;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class CatalogImageWebConfig implements WebMvcConfigurer {

    private final CatalogImagesProperties properties;

    public CatalogImageWebConfig(CatalogImagesProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String publicPath = properties.normalizedPublicPath();
        String location = Path.of(properties.getDirectory()).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler(publicPath + "/**")
                .addResourceLocations(location);
    }
}
