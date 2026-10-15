package com.tienda.pos.whatsapp;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.product.Product;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.*;

@Entity
@Table(name = "whatsapp_manual_promotion",
        uniqueConstraints = @UniqueConstraint(name = "uk_whatsapp_manual_promotion_request",
                columnNames = {"tenant_id", "request_key"}))
public class WhatsappManualPromotion extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private TenantWhatsappConnection connection;
    @Column(name = "request_key", nullable = false, length = 80)
    private String requestKey;
    @Column(nullable = false, columnDefinition = "text")
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ManualPromotionStatus status = ManualPromotionStatus.PROCESSING;
    @Column(nullable = false)
    private int acceptedCount;
    @Column(nullable = false)
    private int failedCount;

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public TenantWhatsappConnection getConnection() { return connection; }
    public void setConnection(TenantWhatsappConnection connection) { this.connection = connection; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public ManualPromotionStatus getStatus() { return status; }
    public void setStatus(ManualPromotionStatus status) { this.status = status; }
    public int getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
}
