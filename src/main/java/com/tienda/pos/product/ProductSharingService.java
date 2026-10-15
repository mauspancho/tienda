package com.tienda.pos.product;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.whatsapp.ManualWhatsappPromotionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@NormalMode
public class ProductSharingService {

    private final ProductRepository products;
    private final ProductImageService images;
    private final ManualWhatsappPromotionService whatsappPromotions;
    private final CurrentTenant currentTenant;

    public ProductSharingService(ProductRepository products, ProductImageService images,
                                 ManualWhatsappPromotionService whatsappPromotions,
                                 CurrentTenant currentTenant) {
        this.products = products;
        this.images = images;
        this.whatsappPromotions = whatsappPromotions;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public SharePreview preview(Long productId) {
        Product product = requireProduct(productId);
        ManualWhatsappPromotionService.Preview whatsapp = whatsappPromotions.preview(productId);
        boolean hasImage = product.getImageUrl() != null && !product.getImageUrl().isBlank();
        boolean downloadableImage = hasImage && images.isLocalImage(product.getImageUrl());
        return new SharePreview(product.getId(), product.getName(), product.getBrand(), product.getPresentation(),
                product.getDescription(), product.getSalePrice(), hasImage ? product.getImageUrl() : null,
                downloadableImage ? "/admin/products/" + product.getId() + "/share-image" : null,
                whatsapp.suggestedMessage(), whatsapp.ready(), whatsapp.individualReady(), whatsapp.warning(),
                whatsapp.individualWarning(), whatsapp.groupNames(), whatsapp.groupCount());
    }

    @Transactional(readOnly = true)
    public ProductImageService.ProductImagePayload image(Long productId) {
        Product product = requireProduct(productId);
        if (!images.isLocalImage(product.getImageUrl())) {
            throw new DomainException("Este producto no tiene una imagen local disponible para descargar.");
        }
        return images.readLocalImage(product.getImageUrl());
    }

    private Product requireProduct(Long productId) {
        return products.findDetailedByIdAndTenantId(productId, currentTenant.id())
                .orElseThrow(() -> new DomainException("Producto no encontrado para esta tienda."));
    }

    public record SharePreview(Long id, String name, String brand, String presentation, String description,
                               BigDecimal salePrice, String imageUrl, String imageDownloadUrl,
                               String suggestedMessage, boolean whatsappReady, boolean whatsappIndividualReady,
                               String whatsappWarning, String whatsappIndividualWarning,
                               List<String> whatsappGroups, int whatsappGroupCount) {
    }
}
