package com.tienda.pos.platform;

import com.tienda.pos.branch.Branch;
import com.tienda.pos.business.Business;
import com.tienda.pos.cash.CashRegister;
import com.tienda.pos.category.Category;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.customer.Customer;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.expense.ExpenseCategory;
import com.tienda.pos.role.Role;
import com.tienda.pos.settings.BusinessSettings;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.warehouse.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NormalMode
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantProvisioningService {
    private final EntityManager em;
    private final Validator validator;
    private final PasswordEncoder encoder;

    public TenantProvisioningService(EntityManager em, Validator validator, PasswordEncoder encoder) {
        this.em = em;
        this.validator = validator;
        this.encoder = encoder;
    }

    @Transactional
    public Long create(TenantCreateForm form) {
        validator.validate(form).stream().findFirst()
                .ifPresent(error -> { throw new DomainException(error.getMessage()); });
        if (em.createQuery("select count(t) from Tenant t where t.code = :code", Long.class)
                .setParameter("code", form.getCode()).getSingleResult() > 0) {
            throw new DomainException("El codigo de tienda ya existe.");
        }
        if (em.createQuery("select count(u) from AppUser u where lower(u.username) = lower(:username)", Long.class)
                .setParameter("username", form.getAdminUsername()).getSingleResult() > 0) {
            throw new DomainException("El nombre de usuario ya existe.");
        }

        Tenant tenant = new Tenant();
        tenant.setCode(form.getCode());
        tenant.setName(form.getName().trim());
        em.persist(tenant);

        Business business = new Business();
        business.setTenant(tenant);
        PlatformTenantService.applyBusiness(business, form);
        em.persist(business);

        BusinessSettings settings = new BusinessSettings();
        settings.setTenant(tenant);
        settings.setBusiness(business);
        PlatformTenantService.applySettings(settings, form);
        em.persist(settings);

        Branch branch = new Branch();
        branch.setTenant(tenant);
        branch.setBusiness(business);
        branch.setCode("MATRIZ");
        branch.setName("Sucursal principal");
        branch.setAddress(form.getAddress().trim());
        branch.setPhone(form.getPhone().trim());
        branch.setTimezone(form.getTimezone());
        em.persist(branch);

        Warehouse warehouse = new Warehouse();
        warehouse.setTenant(tenant);
        warehouse.setBranch(branch);
        warehouse.setCode("PRINCIPAL");
        warehouse.setName("Almac\u00e9n principal");
        warehouse.setMainWarehouse(true);
        em.persist(warehouse);

        CashRegister register = new CashRegister();
        register.setTenant(tenant);
        register.setBranch(branch);
        register.setCode("CAJA01");
        register.setName("Caja principal");
        em.persist(register);

        AppUser admin = new AppUser();
        admin.setTenant(tenant);
        admin.setUsername(form.getAdminUsername());
        admin.setFirstName(form.getAdminFirstName().trim());
        admin.setLastName(form.getAdminLastName().trim());
        admin.setEmail(PlatformTenantService.optional(form.getAdminEmail()));
        admin.setPasswordHash(encoder.encode(form.getPassword()));
        admin.getRoles().add(em.createQuery("select r from Role r where r.name = 'ROLE_ADMIN'", Role.class).getSingleResult());
        em.persist(admin);

        Customer customer = new Customer();
        customer.setTenant(tenant);
        customer.setName("P\u00fablico General");
        em.persist(customer);
        for (String name : new String[]{"Refrescos", "Botanas", "Pan", "L\u00e1cteos", "Abarrotes",
                "Limpieza", "Higiene", "Dulces", "Otros"}) {
            Category category = new Category();
            category.setTenant(tenant);
            category.setName(name);
            em.persist(category);
        }
        for (String name : new String[]{"Servicios", "Renta", "Insumos", "Transporte", "Mantenimiento", "Otros"}) {
            ExpenseCategory category = new ExpenseCategory();
            category.setTenant(tenant);
            category.setName(name);
            em.persist(category);
        }
        em.flush();
        return tenant.getId();
    }
}
