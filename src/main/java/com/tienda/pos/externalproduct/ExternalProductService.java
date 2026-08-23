package com.tienda.pos.externalproduct;

import com.tienda.pos.common.NormalMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@NormalMode
public class ExternalProductService {

    private static final Logger log = LoggerFactory.getLogger(ExternalProductService.class);

    private final List<ExternalProductProvider> providers;

    public ExternalProductService(List<ExternalProductProvider> providers) {
        this.providers = providers;
    }

    public Optional<ExternalProductDto> findByBarcode(String barcode) {
        for (ExternalProductProvider provider : providers) {
            try {
                Optional<ExternalProductDto> result = provider.findByBarcode(barcode);
                log.info("External product lookup barcode={} provider={} result={}", barcode, provider.name(), result.isPresent() ? "FOUND" : "NOT_FOUND");
                if (result.isPresent()) {
                    return result;
                }
            } catch (ExternalProductLookupException ex) {
                log.warn("External product lookup barcode={} provider={} result=ERROR message={}", barcode, provider.name(), ex.getMessage());
                throw ex;
            }
        }
        return Optional.empty();
    }
}
