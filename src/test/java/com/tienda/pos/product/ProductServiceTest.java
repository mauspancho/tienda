package com.tienda.pos.product;

import com.tienda.pos.category.Category;
import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.externalproduct.ExternalProductDto;
import com.tienda.pos.externalproduct.ExternalProductLookupException;
import com.tienda.pos.externalproduct.ExternalProductService;
import com.tienda.pos.inventory.InventoryMovement;
import com.tienda.pos.inventory.InventoryMovementRepository;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.supplier.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
    private final InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
    private final ExternalProductService externalProductService = mock(ExternalProductService.class);
    private final ProductService service = new ProductService(productRepository, categoryRepository,
            supplierRepository, movementRepository, externalProductService);

    @Test
    void calculatesEan13CheckDigit() {
        assertThat(ProductService.ean13CheckDigit("750105530000")).isEqualTo(6);
    }

    @Test
    void existingLocalBarcodeDoesNotCallExternalProvider() {
        Product product = product("7501055300006");
        when(productRepository.findByBarcode("7501055300006")).thenReturn(Optional.of(product));

        ProductBarcodeLookupResult result = service.lookupByBarcode("7501055300006");

        assertThat(result.status()).isEqualTo(ProductBarcodeLookupStatus.LOCAL_FOUND);
        assertThat(result.productId()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("Coca-Cola 600 ml");
        verifyNoInteractions(externalProductService);
    }

    @Test
    void mapsExternalProductWhenBarcodeIsNotLocal() {
        Category category = new Category();
        category.setId(3L);
        category.setName("Bebidas");
        when(productRepository.findByBarcode("7501055300006")).thenReturn(Optional.empty());
        when(externalProductService.findByBarcode("7501055300006")).thenReturn(Optional.of(new ExternalProductDto(
                "7501055300006", "Coca-Cola Original", "Coca-Cola", "600 ml", "Bebidas", "https://img.test/coke.jpg")));
        when(categoryRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(category));

        ProductBarcodeLookupResult result = service.lookupByBarcode("7501055300006");

        assertThat(result.status()).isEqualTo(ProductBarcodeLookupStatus.EXTERNAL_FOUND);
        assertThat(result.name()).isEqualTo("Coca-Cola Original");
        assertThat(result.brand()).isEqualTo("Coca-Cola");
        assertThat(result.presentation()).isEqualTo("600 ml");
        assertThat(result.categorySuggestion()).isEqualTo("Bebidas");
        assertThat(result.categoryId()).isEqualTo(3L);
        assertThat(result.imageUrl()).isEqualTo("https://img.test/coke.jpg");
    }

    @Test
    void returnsNotFoundWhenExternalProviderHasNoProduct() {
        when(productRepository.findByBarcode("9999999999999")).thenReturn(Optional.empty());
        when(externalProductService.findByBarcode("9999999999999")).thenReturn(Optional.empty());

        ProductBarcodeLookupResult result = service.lookupByBarcode("9999999999999");

        assertThat(result.status()).isEqualTo(ProductBarcodeLookupStatus.NOT_FOUND);
        assertThat(result.barcode()).isEqualTo("9999999999999");
    }

    @Test
    void returnsExternalErrorWhenProviderFails() {
        when(productRepository.findByBarcode("7501055300006")).thenReturn(Optional.empty());
        when(externalProductService.findByBarcode("7501055300006"))
                .thenThrow(new ExternalProductLookupException("timeout"));

        ProductBarcodeLookupResult result = service.lookupByBarcode("7501055300006");

        assertThat(result.status()).isEqualTo(ProductBarcodeLookupStatus.EXTERNAL_ERROR);
        assertThat(result.barcode()).isEqualTo("7501055300006");
    }

    @Test
    void createsInitialStockMovementWhenSavingNewProduct() {
        ProductForm form = new ProductForm();
        form.setCode("PRD-12345678");
        form.setBarcode("7501055300006");
        form.setName("Coca-Cola 600 ml");
        form.setPurchaseCost(new BigDecimal("14.00"));
        form.setSalePrice(new BigDecimal("20.00"));
        form.setCurrentStock(new BigDecimal("24.000"));
        form.setMinimumStock(BigDecimal.ZERO);
        form.setUnit(UnitType.PIEZA);
        form.setActive(true);
        when(productRepository.findByCode("PRD-12345678")).thenReturn(Optional.empty());
        when(productRepository.findByBarcode("7501055300006")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        Product saved = service.save(form);

        assertThat(saved.getCurrentStock()).isEqualByComparingTo(new BigDecimal("24.000"));
        ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(movementCaptor.capture());
        InventoryMovement movement = movementCaptor.getValue();
        assertThat(movement.getProduct()).isSameAs(saved);
        assertThat(movement.getMovementType()).isEqualTo(InventoryMovementType.INITIAL_STOCK);
        assertThat(movement.getPreviousStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(movement.getQuantity()).isEqualByComparingTo(new BigDecimal("24.000"));
        assertThat(movement.getNewStock()).isEqualByComparingTo(new BigDecimal("24.000"));
    }

    @Test
    void rejectsDuplicatedBarcode() {
        Product existing = product("7501055300006");
        ProductForm form = new ProductForm();
        form.setCode("PRD-87654321");
        form.setBarcode("7501055300006");
        form.setName("Producto nuevo");
        form.setPurchaseCost(BigDecimal.ONE);
        form.setSalePrice(BigDecimal.TEN);
        form.setCurrentStock(BigDecimal.ZERO);
        form.setMinimumStock(BigDecimal.ZERO);
        form.setUnit(UnitType.PIEZA);
        when(productRepository.findByCode("PRD-87654321")).thenReturn(Optional.empty());
        when(productRepository.findByBarcode("7501055300006")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("código de barras");
        verify(productRepository, never()).save(any(Product.class));
    }

    private Product product(String barcode) {
        Product product = new Product();
        product.setId(10L);
        product.setCode("PRD-00000010");
        product.setBarcode(barcode);
        product.setName("Coca-Cola 600 ml");
        product.setPurchaseCost(new BigDecimal("14.00"));
        product.setSalePrice(new BigDecimal("20.00"));
        product.setCurrentStock(new BigDecimal("5.000"));
        product.setMinimumStock(BigDecimal.ZERO);
        product.setUnit(UnitType.PIEZA);
        return product;
    }
}
