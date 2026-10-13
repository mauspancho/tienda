package com.tienda.pos.api.v1.customer;

import com.tienda.pos.customer.Customer;
import com.tienda.pos.customer.CustomerForm;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public final class ApiCustomerModels {
    private ApiCustomerModels() {}
    public record CustomerRequest(@NotBlank String name, String lastName, String phone, String notes, Boolean active) {
        CustomerForm toForm() {
            CustomerForm form = new CustomerForm();
            form.setName(name); form.setLastName(lastName); form.setPhone(phone); form.setNotes(notes);
            form.setActive(active == null || active);
            return form;
        }
    }
    public record CustomerResponse(Long id, String name, String lastName, String fullName, String phone,
                                   String notes, boolean active, LocalDateTime registeredAt) {
        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.getId(), customer.getName(), customer.getLastName(),
                    customer.getFullName(), customer.getPhone(), customer.getNotes(), customer.isActive(), customer.getCreatedAt());
        }
    }
}
