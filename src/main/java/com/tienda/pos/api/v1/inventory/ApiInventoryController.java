package com.tienda.pos.api.v1.inventory;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.api.v1.product.ApiProductModels.ProductResponse;
import com.tienda.pos.inventory.InventoryAdjustmentForm;
import com.tienda.pos.inventory.InventoryMovement;
import com.tienda.pos.inventory.InventoryMovementRepository;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class ApiInventoryController {

    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public ApiInventoryController(InventoryMovementRepository movementRepository,
                                  ProductRepository productRepository, InventoryService inventoryService) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stock")
    public ApiPageResponse<ProductResponse> stock(@RequestParam(defaultValue = "") String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), bounded(size), Sort.by("name").ascending());
        Page<Product> products = productRepository.filter(q == null ? "" : q.trim(), "", "", null,
                null, null, null, null, pageable);
        return ApiPageResponse.from(products, ProductResponse::from);
    }

    @GetMapping("/movements")
    public ApiPageResponse<MovementResponse> movements(@RequestParam(required = false) Long productId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), bounded(size));
        Page<InventoryMovement> movements = productId == null
                ? movementRepository.findAllByOrderByCreatedAtDesc(pageable)
                : movementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        return ApiPageResponse.from(movements, MovementResponse::from);
    }

    @GetMapping("/movements/{id}")
    public MovementResponse movement(@PathVariable Long id) {
        return MovementResponse.from(find(id));
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse adjust(@Valid @RequestBody AdjustmentRequest request) {
        inventoryService.adjust(request.toForm());
        Page<InventoryMovement> recent = movementRepository.findByProductIdOrderByCreatedAtDesc(
                request.productId(), PageRequest.of(0, 1));
        if (recent.isEmpty()) throw new ApiNotFoundException("Movimiento no encontrado.");
        return MovementResponse.from(recent.getContent().getFirst());
    }

    @PostMapping("/movements/{id}/reverse")
    public MovementResponse reverse(@PathVariable Long id) {
        inventoryService.reverseMovement(id);
        return MovementResponse.from(find(id));
    }

    private InventoryMovement find(Long id) {
        return movementRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiNotFoundException("Movimiento no encontrado."));
    }

    private int bounded(int size) { return Math.max(1, Math.min(size, 100)); }

    public record AdjustmentRequest(@NotNull Long productId, @NotNull InventoryMovementType movementType,
                                    @NotNull @DecimalMin("0.001") BigDecimal quantity,
                                    @DecimalMin("0.00") BigDecimal unitCost, String notes) {
        InventoryAdjustmentForm toForm() {
            InventoryAdjustmentForm form = new InventoryAdjustmentForm();
            form.setProductId(productId);
            form.setMovementType(movementType);
            form.setQuantity(quantity);
            form.setUnitCost(unitCost);
            form.setNotes(notes);
            return form;
        }
    }

    public record MovementResponse(Long id, Long productId, String productName,
                                   InventoryMovementType type, BigDecimal quantity,
                                   BigDecimal previousStock, BigDecimal newStock,
                                   BigDecimal unitCost, BigDecimal previousPurchaseCost,
                                   BigDecimal newPurchaseCost, BigDecimal costAdjustment,
                                   String referenceType, Long referenceId, String notes,
                                   boolean reversed, boolean reversible, LocalDateTime createdAt,
                                   LocalDateTime reversedAt, String reversedBy, Long reversalMovementId) {
        static MovementResponse from(InventoryMovement movement) {
            return new MovementResponse(movement.getId(), movement.getProduct().getId(), movement.getProduct().getName(),
                    movement.getMovementType(), movement.getQuantity(), movement.getPreviousStock(),
                    movement.getNewStock(), movement.getUnitCost(), movement.getPreviousPurchaseCost(),
                    movement.getNewPurchaseCost(), movement.getCostAdjustment(), movement.getReferenceType(),
                    movement.getReferenceId(), movement.getNotes(), movement.isReversed(), movement.isReversible(),
                    movement.getCreatedAt(), movement.getReversedAt(), movement.getReversedBy(),
                    movement.getReversalMovementId());
        }
    }
}
