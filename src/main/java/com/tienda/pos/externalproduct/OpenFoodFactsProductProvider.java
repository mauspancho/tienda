package com.tienda.pos.externalproduct;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tienda.pos.common.NormalMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
@NormalMode
public class OpenFoodFactsProductProvider implements ExternalProductProvider {

    private static final String FIELDS = String.join(",",
            "code",
            "product_name",
            "product_name_es",
            "generic_name",
            "brands",
            "quantity",
            "categories",
            "categories_tags",
            "image_front_url",
            "image_url");

    private final ExternalProductsProperties properties;
    private final RestClient restClient;

    public OpenFoodFactsProductProvider(ExternalProductsProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "OpenFoodFacts";
    }

    @Override
    public Optional<ExternalProductDto> findByBarcode(String barcode) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            ResponseEntity<OpenFoodFactsResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/product/{barcode}")
                            .queryParam("fields", FIELDS)
                            .queryParam("lc", "es")
                            .queryParam("tags_lc", "es")
                            .build(barcode))
                    .header(HttpHeaders.USER_AGENT, properties.getUserAgent())
                    .retrieve()
                    .toEntity(OpenFoodFactsResponse.class);
            return map(response.getBody(), barcode);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException | ResourceAccessException ex) {
            throw new ExternalProductLookupException("Open Food Facts no respondió correctamente.", ex);
        } catch (RestClientException ex) {
            throw new ExternalProductLookupException("No fue posible consultar Open Food Facts.", ex);
        }
    }

    private Optional<ExternalProductDto> map(OpenFoodFactsResponse response, String barcode) {
        if (response == null || response.product() == null) {
            return Optional.empty();
        }
        OpenFoodFactsProduct product = response.product();
        String name = firstPresent(product.productNameEs(), product.productName(), product.genericName());
        String imageUrl = firstPresent(product.imageFrontUrl(), product.imageUrl());
        String category = firstCategory(product.categories(), product.categoriesTags());
        if (isBlank(name) && isBlank(product.brands()) && isBlank(product.quantity()) && isBlank(imageUrl)) {
            return Optional.empty();
        }
        return Optional.of(new ExternalProductDto(
                firstPresent(product.code(), response.code(), barcode),
                name,
                blankToNull(product.brands()),
                blankToNull(product.quantity()),
                category,
                imageUrl));
    }

    private String firstCategory(String categories, List<String> categoryTags) {
        if (!isBlank(categories)) {
            return cleanCategory(categories.split(",")[0]);
        }
        if (categoryTags != null && !categoryTags.isEmpty()) {
            return cleanCategory(categoryTags.get(0));
        }
        return null;
    }

    private String cleanCategory(String value) {
        if (isBlank(value)) return null;
        String cleaned = value.trim();
        int prefixIndex = cleaned.indexOf(':');
        if (prefixIndex >= 0 && prefixIndex + 1 < cleaned.length()) {
            cleaned = cleaned.substring(prefixIndex + 1);
        }
        cleaned = cleaned.replace('-', ' ').trim();
        if (cleaned.isEmpty()) return null;
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return null;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenFoodFactsResponse(String code, OpenFoodFactsProduct product) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenFoodFactsProduct(
            String code,
            @JsonProperty("product_name") String productName,
            @JsonProperty("product_name_es") String productNameEs,
            @JsonProperty("generic_name") String genericName,
            String brands,
            String quantity,
            String categories,
            @JsonProperty("categories_tags") List<String> categoriesTags,
            @JsonProperty("image_front_url") String imageFrontUrl,
            @JsonProperty("image_url") String imageUrl
    ) {
    }
}
