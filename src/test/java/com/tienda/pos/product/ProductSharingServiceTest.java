package com.tienda.pos.product;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.whatsapp.ManualWhatsappPromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSharingServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageService images = mock(ProductImageService.class);
    private final ManualWhatsappPromotionService whatsapp = mock(ManualWhatsappPromotionService.class);
    private final CurrentTenant currentTenant = mock(CurrentTenant.class);
    private final ProductSharingService service = new ProductSharingService(products, images, whatsapp, currentTenant);

    @BeforeEach
    void setUp() {
        when(currentTenant.id()).thenReturn(17L);
    }

    @Test
    void buildsPreviewWithCurrentProductAndExistingWhatsappConfiguration() {
        Product product = product(41L, "/uploads/products/coffee.jpg");
        when(products.findDetailedByIdAndTenantId(41L, 17L)).thenReturn(Optional.of(product));
        when(images.isLocalImage(product.getImageUrl())).thenReturn(true);
        when(whatsapp.preview(41L)).thenReturn(new ManualWhatsappPromotionService.Preview(
                true, true, null, null, "Oferta de café", List.of("Clientes", "Mayoreo"), 2));

        ProductSharingService.SharePreview preview = service.preview(41L);

        assertThat(preview.name()).isEqualTo("Café");
        assertThat(preview.imageDownloadUrl()).isEqualTo("/admin/products/41/share-image");
        assertThat(preview.suggestedMessage()).isEqualTo("Oferta de café");
        assertThat(preview.whatsappGroups()).containsExactly("Clientes", "Mayoreo");
    }

    @Test
    void productFromAnotherTenantCannotBePreviewed() {
        when(products.findDetailedByIdAndTenantId(99L, 17L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(99L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("esta tienda");

        verify(whatsapp, never()).preview(99L);
    }

    @Test
    void localImageDownloadIsTenantScopedAndReturnsOriginalBytes() {
        Product product = product(41L, "/uploads/products/coffee.webp");
        ProductImageService.ProductImagePayload payload = new ProductImageService.ProductImagePayload(
                "coffee.webp", "image/webp", new byte[]{1, 2, 3});
        when(products.findDetailedByIdAndTenantId(41L, 17L)).thenReturn(Optional.of(product));
        when(images.isLocalImage(product.getImageUrl())).thenReturn(true);
        when(images.readLocalImage(product.getImageUrl())).thenReturn(payload);

        assertThat(service.image(41L)).isSameAs(payload);
        verify(images).readLocalImage("/uploads/products/coffee.webp");
    }

    @Test
    void remoteOrMissingImageIsNotExposedThroughPrivateDownload() {
        Product product = product(42L, "https://images.example/product.jpg");
        when(products.findDetailedByIdAndTenantId(42L, 17L)).thenReturn(Optional.of(product));
        when(images.isLocalImage(product.getImageUrl())).thenReturn(false);

        assertThatThrownBy(() -> service.image(42L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("imagen local");

        verify(images, never()).readLocalImage(product.getImageUrl());
    }

    private Product product(Long id, String imageUrl) {
        Product product = new Product();
        product.setId(id);
        product.setName("Café");
        product.setBrand("Sierra");
        product.setPresentation("500 g");
        product.setDescription("Tueste medio");
        product.setSalePrice(BigDecimal.valueOf(82));
        product.setImageUrl(imageUrl);
        return product;
    }
}
