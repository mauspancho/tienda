package com.tienda.pos.product;

import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@NormalMode
public class ProductBrandService {

    private static final int MAX_SUGGESTIONS = 10;

    private final ProductRepository productRepository;
    private final ProductBrandNormalizer normalizer;

    public ProductBrandService(ProductRepository productRepository, ProductBrandNormalizer normalizer) {
        this.productRepository = productRepository;
        this.normalizer = normalizer;
    }

    @Transactional(readOnly = true)
    public String canonicalize(Long tenantId, String value) {
        String normalized = normalizer.normalize(value);
        if (normalized == null) {
            return null;
        }
        String key = normalizer.key(normalized);
        return productRepository.findDistinctBrands(tenantId).stream()
                .filter(existing -> normalizer.key(existing).equals(key))
                .map(normalizer::normalize)
                .findFirst()
                .orElse(normalized);
    }

    @Transactional(readOnly = true)
    public List<String> suggest(Long tenantId, String rawQuery) {
        String query = normalizer.key(rawQuery);
        if (query.codePointCount(0, query.length()) < 2) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String existing : productRepository.findDistinctBrands(tenantId)) {
            String normalized = normalizer.normalize(existing);
            String key = normalizer.key(existing);
            if (normalized != null && key.contains(query)) {
                unique.putIfAbsent(key, normalized);
            }
        }
        return unique.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, String> entry) -> !entry.getKey().startsWith(query))
                        .thenComparing(Map.Entry::getValue, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_SUGGESTIONS)
                .map(Map.Entry::getValue)
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandNormalizationReport report(Long tenantId) {
        return buildReport(productRepository.findProductsWithBrand(tenantId));
    }

    @Transactional
    public BrandNormalizationResult normalizeExisting(Long tenantId) {
        List<Product> products = productRepository.findProductsWithBrandForUpdate(tenantId);
        BrandNormalizationReport before = buildReport(products);
        int changed = 0;
        for (Product product : products) {
            String normalized = normalizer.normalize(product.getBrand());
            if (!java.util.Objects.equals(product.getBrand(), normalized)) {
                product.setBrand(normalized);
                changed++;
            }
        }
        productRepository.saveAll(products);
        return new BrandNormalizationResult(changed, before);
    }

    private BrandNormalizationReport buildReport(List<Product> products) {
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        int changedProducts = 0;
        for (Product product : products) {
            String original = product.getBrand();
            String canonical = normalizer.normalize(original);
            if (canonical == null) {
                continue;
            }
            boolean changed = !canonical.equals(original);
            if (changed) {
                changedProducts++;
            }
            MutableGroup group = groups.computeIfAbsent(normalizer.key(canonical), ignored -> new MutableGroup(canonical));
            group.productCount++;
            if (changed) {
                group.changedProductCount++;
            }
            group.variants.add(original);
        }
        List<BrandNormalizationGroup> affected = groups.values().stream()
                .filter(group -> group.changedProductCount > 0 || group.variants.size() > 1)
                .map(group -> new BrandNormalizationGroup(group.canonical, group.productCount,
                        group.changedProductCount, new ArrayList<>(group.variants)))
                .sorted(Comparator.comparing(BrandNormalizationGroup::canonicalBrand, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new BrandNormalizationReport(products.size(), changedProducts, affected);
    }

    private static final class MutableGroup {
        private final String canonical;
        private final LinkedHashSet<String> variants = new LinkedHashSet<>();
        private int productCount;
        private int changedProductCount;

        private MutableGroup(String canonical) {
            this.canonical = canonical;
        }
    }

    public record BrandNormalizationReport(int productsWithBrand, int productsAffected,
                                           List<BrandNormalizationGroup> groups) {
    }

    public record BrandNormalizationGroup(String canonicalBrand, int productCount,
                                          int changedProductCount, List<String> variants) {
    }

    public record BrandNormalizationResult(int changedProducts, BrandNormalizationReport reportBefore) {
    }
}
