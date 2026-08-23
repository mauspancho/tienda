package com.tienda.pos.user;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.role.RoleRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NormalMode
public class UserService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public UserService(AppUserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Transactional
    public void create(UserForm form) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new DomainException("El usuario ya existe.");
        }
        if (!form.hasPassword() || form.getPassword().length() < 8) {
            throw new DomainException("La contraseña debe tener al menos 8 caracteres.");
        }
        AppUser user = new AppUser();
        applyEditableFields(user, form);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void update(Long id, UserForm form) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new DomainException("Usuario no encontrado."));
        String requestedUsername = form.getUsername().trim();
        userRepository.findByUsername(requestedUsername)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new DomainException("El usuario ya existe."); });
        applyEditableFields(user, form);
        if (form.hasPassword()) {
            if (form.getPassword().length() < 8) {
                throw new DomainException("La contraseña debe tener al menos 8 caracteres.");
            }
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }
        userRepository.save(user);
    }

    @Transactional
    public void toggleActive(Long id) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new DomainException("Usuario no encontrado."));
        if (user.getUsername().equals(CurrentUser.username()) && user.isActive()) {
            throw new DomainException("No puedes desactivar tu propio usuario activo.");
        }
        user.setActive(!user.isActive());
        userRepository.save(user);
    }

    @Transactional
    public void deleteOrDeactivate(Long id) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new DomainException("Usuario no encontrado."));
        if (user.getUsername().equals(CurrentUser.username())) {
            throw new DomainException("No puedes eliminar tu propio usuario.");
        }
        if (hasHistory(id)) {
            user.setActive(false);
            userRepository.save(user);
            return;
        }
        user.getRoles().clear();
        userRepository.delete(user);
    }

    private void applyEditableFields(AppUser user, UserForm form) {
        user.setUsername(form.getUsername().trim());
        user.setFirstName(form.getFirstName().trim());
        user.setLastName(form.getLastName().trim());
        user.setEmail(blankToNull(form.getEmail()));
        user.setActive(form.isActive());
        user.getRoles().clear();
        if (form.isAdmin()) {
            user.getRoles().add(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        }
        if (form.isCashier()) {
            user.getRoles().add(roleRepository.findByName("ROLE_CAJERO").orElseThrow());
        }
        if (user.getRoles().isEmpty()) {
            throw new DomainException("Selecciona al menos un rol.");
        }
    }

    private boolean hasHistory(Long userId) {
        return count("select count(s) from Sale s where s.cashier.id = :userId", userId) > 0
                || count("select count(p) from Purchase p where p.user.id = :userId", userId) > 0
                || count("select count(m) from InventoryMovement m where m.user.id = :userId", userId) > 0
                || count("select count(c) from CashRegisterSession c where c.cashier.id = :userId", userId) > 0
                || count("select count(m) from CashMovement m where m.user.id = :userId", userId) > 0
                || count("select count(e) from Expense e where e.user.id = :userId", userId) > 0
                || count("select count(a) from AuditLog a where a.user.id = :userId", userId) > 0;
    }

    private long count(String jpql, Long userId) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}