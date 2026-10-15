package com.tienda.pos.whatsapp;

import com.tienda.pos.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "whatsapp_manual_promotion_delivery",
        uniqueConstraints = @UniqueConstraint(name = "uk_whatsapp_manual_promotion_delivery_recipient",
                columnNames = {"promotion_id", "recipient_type", "recipient_address"}))
public class WhatsappManualPromotionDelivery extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private WhatsappManualPromotion promotion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private WhatsappGroup group;
    @Column(nullable = false)
    private String groupName;
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private ManualPromotionRecipientType recipientType = ManualPromotionRecipientType.GROUP;
    @Column(name = "recipient_address", nullable = false, length = 100)
    private String recipientAddress;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ManualPromotionDeliveryStatus status = ManualPromotionDeliveryStatus.PENDING;
    private String providerMessageId;
    @Column(length = 500)
    private String errorMessage;

    public WhatsappManualPromotion getPromotion() { return promotion; }
    public void setPromotion(WhatsappManualPromotion promotion) { this.promotion = promotion; }
    public WhatsappGroup getGroup() { return group; }
    public void setGroup(WhatsappGroup group) { this.group = group; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public ManualPromotionRecipientType getRecipientType() { return recipientType; }
    public void setRecipientType(ManualPromotionRecipientType recipientType) { this.recipientType = recipientType; }
    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }
    public ManualPromotionDeliveryStatus getStatus() { return status; }
    public void setStatus(ManualPromotionDeliveryStatus status) { this.status = status; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
