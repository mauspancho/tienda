package com.tienda.pos.customer;

import jakarta.validation.constraints.NotBlank;

public class CustomerForm {
    @NotBlank
    private String name;
    private String lastName;
    private String phone;
    private String notes;
    private boolean active = true;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
