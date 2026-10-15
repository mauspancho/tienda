package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WhatsappProviderRepository extends JpaRepository<WhatsappProvider, Long> {
    Optional<WhatsappProvider> findFirstByEnabledTrueOrderByIdAsc();
    List<WhatsappProvider> findAllByOrderByNameAsc();
}
