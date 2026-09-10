package com.tienda.pos.platform;

import com.tienda.pos.business.Business;
import com.tienda.pos.branch.Branch;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.settings.BusinessSettings;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@NormalMode
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Transactional(readOnly = true)
public class PlatformTenantService {
    private final EntityManager em;
    private final Validator validator;
    private final PasswordEncoder encoder;

    public PlatformTenantService(EntityManager em, Validator validator, PasswordEncoder encoder) {
        this.em = em;
        this.validator = validator;
        this.encoder = encoder;
    }

    public record AdminView(Long id, String username, String fullName, boolean active, boolean platform) {}
    public record TenantView(Long id, String code, String name, boolean active, String businessName,
                             String adminUsername, long users, long products, LocalDateTime createdAt) {}
    public record Detail(TenantView tenant, TenantForm form, List<AdminView> admins) {}

    public Page<TenantView> search(String query, int page) {
        String term = "%" + query.trim().toLowerCase(Locale.ROOT).replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        String condition = " where lower(t.name) like :q escape '!' or lower(t.code) like :q escape '!'";
        long total = em.createQuery("select count(t) from Tenant t" + condition, Long.class)
                .setParameter("q", term).getSingleResult();
        PageRequest pageable = PageRequest.of(Math.max(0, page), 20);
        List<TenantView> rows = em.createQuery("select t from Tenant t" + condition + " order by t.id desc", Tenant.class)
                .setParameter("q", term).setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(20)
                .getResultList().stream().map(this::view).toList();
        return new PageImpl<>(rows, pageable, total);
    }

    public Detail detail(Long id) {
        Tenant tenant = tenant(id);
        Business business = business(id);
        BusinessSettings settings = settings(id);
        TenantForm form = new TenantForm();
        form.setName(tenant.getName());
        form.setBusinessName(business.getName());
        form.setLegalName(business.getLegalName());
        form.setTaxId(business.getTaxId());
        form.setPhone(business.getPhone() == null ? settings.getPhone() : business.getPhone());
        form.setEmail(business.getEmail());
        form.setAddress(settings.getAddress());
        form.setCurrency(settings.getCurrency());
        form.setCurrencySymbol(settings.getCurrencySymbol());
        form.setTimezone(settings.getTimezone());
        return new Detail(view(tenant), form, admins(id).stream()
                .map(u -> new AdminView(u.getId(), u.getUsername(), u.fullName(), u.isActive(), u.hasRole("ROLE_PLATFORM_ADMIN")))
                .toList());
    }

    @Transactional
    public void update(Long id, TenantForm form) {
        validate(form);
        Tenant tenant = tenant(id);
        tenant.setName(form.getName().trim());
        applyBusiness(business(id), form);
        applySettings(settings(id), form);
        // Only the primary branch's shared contact settings change; no relationships are reassigned.
        em.createQuery("select b from Branch b where b.tenant.id = :tenant order by b.id", Branch.class)
                .setParameter("tenant", id).setMaxResults(1).getResultList().forEach(branch -> {
                    branch.setAddress(form.getAddress().trim());
                    branch.setPhone(form.getPhone().trim());
                    branch.setTimezone(form.getTimezone());
                });
    }

    @Transactional
    public void toggle(Long id) {
        Tenant tenant = em.find(Tenant.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (tenant == null) throw notFound();
        tenant.setActive(!tenant.isActive());
    }

    @Transactional
    public void reactivateAdmin(Long tenantId, Long adminId) {
        admin(tenantId, adminId).setActive(true);
    }

    @Transactional
    public void resetPassword(Long tenantId, Long adminId, PasswordResetForm form) {
        validate(form);
        admin(tenantId, adminId).setPasswordHash(encoder.encode(form.getPassword()));
    }

    private AppUser admin(Long tenantId, Long adminId) {
        return em.createQuery("select u from AppUser u join u.roles r where u.tenant.id = :tenant "
                        + "and u.id = :id and r.name = 'ROLE_ADMIN'", AppUser.class)
                .setParameter("tenant", tenantId).setParameter("id", adminId).getResultStream()
                .findFirst().orElseThrow(PlatformTenantService::notFound);
    }

    private TenantView view(Tenant tenant) {
        Long id = tenant.getId();
        String name = em.createQuery("select b.name from Business b where b.tenant.id = :tenant order by b.id", String.class)
                .setParameter("tenant", id).setMaxResults(1).getResultStream().findFirst().orElse("-");
        String admin = admins(id).stream().map(AppUser::getUsername).findFirst().orElse("-");
        return new TenantView(id, tenant.getCode(), tenant.getName(), tenant.isActive(), name, admin,
                count("AppUser", id), count("Product", id), tenant.getCreatedAt());
    }

    private long count(String entity, Long id) {
        return em.createQuery("select count(e) from " + entity + " e where e.tenant.id = :tenant", Long.class)
                .setParameter("tenant", id).getSingleResult();
    }

    private List<AppUser> admins(Long id) {
        return em.createQuery("select u from AppUser u join u.roles r where u.tenant.id = :tenant "
                        + "and r.name = 'ROLE_ADMIN' order by u.id", AppUser.class)
                .setParameter("tenant", id).getResultList();
    }

    private Tenant tenant(Long id) {
        Tenant tenant = em.find(Tenant.class, id);
        if (tenant == null) throw notFound();
        return tenant;
    }

    private Business business(Long id) {
        return em.createQuery("select b from Business b where b.tenant.id = :tenant order by b.id", Business.class)
                .setParameter("tenant", id).setMaxResults(1).getResultStream().findFirst().orElseThrow(PlatformTenantService::notFound);
    }

    private BusinessSettings settings(Long id) {
        return em.createQuery("select s from BusinessSettings s where s.tenant.id = :tenant order by s.id", BusinessSettings.class)
                .setParameter("tenant", id).setMaxResults(1).getResultStream().findFirst().orElseThrow(PlatformTenantService::notFound);
    }

    private void validate(Object form) {
        validator.validate(form).stream().findFirst().ifPresent(error -> { throw new DomainException(error.getMessage()); });
    }

    static void applyBusiness(Business business, TenantForm form) {
        business.setName(form.getBusinessName().trim());
        business.setLegalName(optional(form.getLegalName()));
        business.setTaxId(optional(form.getTaxId()));
        business.setPhone(form.getPhone().trim());
        business.setEmail(optional(form.getEmail()));
    }

    static void applySettings(BusinessSettings settings, TenantForm form) {
        settings.setStoreName(form.getBusinessName().trim());
        settings.setTaxId(optional(form.getTaxId()));
        settings.setPhone(form.getPhone().trim());
        settings.setAddress(form.getAddress().trim());
        settings.setCurrency(form.getCurrency());
        settings.setCurrencySymbol(form.getCurrencySymbol());
        settings.setTimezone(form.getTimezone());
    }

    static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado."); }
}
