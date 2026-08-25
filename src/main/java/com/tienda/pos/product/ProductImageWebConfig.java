package com.tienda.pos.product;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ProductImageWebConfig implements WebMvcConfigurer {

    private final ProductImagesProperties properties;

    public ProductImageWebConfig(ProductImagesProperties properties) {
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
