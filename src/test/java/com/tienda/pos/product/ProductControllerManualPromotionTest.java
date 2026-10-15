package com.tienda.pos.product;

import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.supplier.SupplierRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.whatsapp.ManualWhatsappPromotionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerManualPromotionTest {
    @Test
    void endpointAcceptsOnlyProductMessageAndIdempotencyKey() throws Exception {
        ManualWhatsappPromotionService promotions = mock(ManualWhatsappPromotionService.class);
        when(promotions.send(7L, "Mi anuncio", "requestkey123456789", false, null))
                .thenReturn(new ManualWhatsappPromotionService.Result("COMPLETED", 2, 0, false, false,
                        List.of(new ManualWhatsappPromotionService.DeliveryResult("Grupo 1", "ACCEPTED", null))));
        ProductController controller = new ProductController(mock(ProductRepository.class), mock(ProductService.class),
                mock(CategoryRepository.class), mock(SupplierRepository.class), mock(BarcodeLabelService.class),
                mock(CurrentTenant.class), promotions);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/admin/products/7/manual-whatsapp-promotion")
                        .param("message", "Mi anuncio")
                        .param("requestKey", "requestkey123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.failed").value(0));

        verify(promotions).send(7L, "Mi anuncio", "requestkey123456789", false, null);
    }

    @Test
    void endpointPassesExclusiveIndividualDestinationToService() throws Exception {
        ManualWhatsappPromotionService promotions = mock(ManualWhatsappPromotionService.class);
        when(promotions.send(8L, "Oferta", "requestkey123456789", true, "+52 55-1234-5678"))
                .thenReturn(new ManualWhatsappPromotionService.Result("COMPLETED", 1, 0, false, false,
                        List.of(new ManualWhatsappPromotionService.DeliveryResult("+525512345678", "ACCEPTED", null))));
        ProductController controller = new ProductController(mock(ProductRepository.class), mock(ProductService.class),
                mock(CategoryRepository.class), mock(SupplierRepository.class), mock(BarcodeLabelService.class),
                mock(CurrentTenant.class), promotions);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/admin/products/8/manual-whatsapp-promotion")
                        .param("message", "Oferta")
                        .param("requestKey", "requestkey123456789")
                        .param("sendToNumber", "true")
                        .param("phone", "+52 55-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        verify(promotions).send(8L, "Oferta", "requestkey123456789", true, "+52 55-1234-5678");
    }
}
