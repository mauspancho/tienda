package com.tienda.pos.api.v1.settings;

import com.tienda.pos.settings.BusinessSettings;
import com.tienda.pos.settings.SettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasRole('ADMIN')")
public class ApiSettingsController {

    private final SettingsService service;

    public ApiSettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsResponse current() {
        return SettingsResponse.from(service.current());
    }

    @PutMapping(consumes = "application/json")
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return SettingsResponse.from(service.save(request.toEntity(), null, request.removeLogo()));
    }

    @PutMapping(consumes = "multipart/form-data")
    public SettingsResponse updateWithLogo(@Valid @RequestPart("settings") SettingsRequest request,
                                           @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return SettingsResponse.from(service.save(request.toEntity(), logo, request.removeLogo()));
    }

    public record SettingsRequest(@NotBlank String storeName, String address, String phone, String taxId,
                                  @NotBlank String currency, @NotBlank String currencySymbol,
                                  @NotBlank String timezone,
                                  @NotNull @DecimalMin("0.00") BigDecimal defaultTax,
                                  boolean catalogEnabled, String catalogTitle, String catalogSubtitle,
                                  String promotionTitle, boolean negativeStockAllowed, boolean removeLogo) {
        BusinessSettings toEntity() {
            BusinessSettings settings = new BusinessSettings();
            settings.setStoreName(storeName);
            settings.setAddress(address);
            settings.setPhone(phone);
            settings.setTaxId(taxId);
            settings.setCurrency(currency);
            settings.setCurrencySymbol(currencySymbol);
            settings.setTimezone(timezone);
            settings.setDefaultTax(defaultTax);
            settings.setCatalogEnabled(catalogEnabled);
            settings.setCatalogTitle(catalogTitle);
            settings.setCatalogSubtitle(catalogSubtitle);
            settings.setPromotionTitle(promotionTitle);
            settings.setNegativeStockAllowed(negativeStockAllowed);
            return settings;
        }
    }

    public record SettingsResponse(String storeName, String address, String phone, String taxId,
                                   String currency, String currencySymbol, String timezone,
                                   BigDecimal defaultTax, String logoPath, boolean catalogEnabled,
                                   String catalogTitle, String catalogSubtitle, String promotionTitle,
                                   boolean negativeStockAllowed) {
        static SettingsResponse from(BusinessSettings settings) {
            return new SettingsResponse(settings.getStoreName(), settings.getAddress(), settings.getPhone(),
                    settings.getTaxId(), settings.getCurrency(), settings.getCurrencySymbol(),
                    settings.getTimezone(), settings.getDefaultTax(), settings.getLogoPath(),
                    settings.isCatalogEnabled(), settings.getCatalogTitle(), settings.getCatalogSubtitle(),
                    settings.getPromotionTitle(), settings.isNegativeStockAllowed());
        }
    }
}
