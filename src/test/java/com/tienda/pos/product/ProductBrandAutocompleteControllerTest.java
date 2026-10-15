package com.tienda.pos.product;

import com.tienda.pos.api.ProductApiController;
import com.tienda.pos.tenant.CurrentTenant;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductBrandAutocompleteControllerTest {

    @Test
    void autocompleteUsesAuthenticatedTenantAndReturnsCanonicalSuggestions() throws Exception {
        ProductBrandService brands = mock(ProductBrandService.class);
        CurrentTenant tenant = mock(CurrentTenant.class);
        when(tenant.id()).thenReturn(19L);
        when(brands.suggest(19L, "ga")).thenReturn(List.of("Gamesa", "Gallo"));
        ProductApiController controller = new ProductApiController(mock(ProductRepository.class),
                mock(ProductService.class), brands, tenant);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/admin/api/products/suggestions").param("field", "brand").param("q", "ga")
                        .param("tenantId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Gamesa"))
                .andExpect(jsonPath("$[1]").value("Gallo"));

        verify(brands).suggest(19L, "ga");
    }
}
