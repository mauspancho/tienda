package com.tienda.pos.settings;

import com.tienda.pos.catalog.CatalogImageService;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@NormalMode
public class SettingsService {

    private final BusinessSettingsRepository repository;
    private final CatalogImageService imageService;

    public SettingsService(BusinessSettingsRepository repository, CatalogImageService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    @Transactional(readOnly = true)
    public BusinessSettings current() {
        return repository.findById(1L).orElseGet(BusinessSettings::new);
    }

    @Transactional
    public BusinessSettings save(BusinessSettings input, MultipartFile logoFile, boolean removeLogo) {
        BusinessSettings current = repository.findById(1L).orElseGet(BusinessSettings::new);
        String previousLogo = current.getLogoPath();
        String newLogo = null;
        try {
            current.setId(1L);
            current.setStoreName(input.getStoreName());
            current.setAddress(input.getAddress());
            current.setPhone(input.getPhone());
            current.setTaxId(input.getTaxId());
            current.setCurrency(input.getCurrency());
            current.setCurrencySymbol(input.getCurrencySymbol());
            current.setTimezone(input.getTimezone());
            current.setDefaultTax(input.getDefaultTax());
            current.setCatalogEnabled(input.isCatalogEnabled());
            current.setCatalogTitle(blankToNull(input.getCatalogTitle()));
            current.setCatalogSubtitle(blankToNull(input.getCatalogSubtitle()));
            current.setPromotionTitle(blankToNull(input.getPromotionTitle()));
            current.setNegativeStockAllowed(input.isNegativeStockAllowed());
            if (removeLogo) current.setLogoPath(null);
            if (logoFile != null && !logoFile.isEmpty()) {
                newLogo = imageService.store(logoFile);
                current.setLogoPath(newLogo);
            }
            BusinessSettings saved = repository.save(current);
            scheduleLogoCleanup(previousLogo, newLogo, removeLogo);
            return saved;
        } catch (RuntimeException ex) {
            if (newLogo != null) imageService.deleteLocalLogo(newLogo);
            throw ex;
        }
    }

    private void scheduleLogoCleanup(String previousLogo, String newLogo, boolean removeLogo) {
        boolean replace = (removeLogo || newLogo != null) && imageService.isLocalLogo(previousLogo);
        if (!replace) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageService.deleteLocalLogo(previousLogo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageService.deleteLocalLogo(previousLogo);
            }
        });
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
