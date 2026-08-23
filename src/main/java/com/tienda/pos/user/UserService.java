package com.tienda.pos.user;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.role.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NormalMode
public class UserService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void create(UserForm form) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new DomainException("El usuario ya existe.");
        }
        if (form.getPassword() == null || form.getPassword().length() < 8) {
            throw new DomainException("La contraseña debe tener al menos 8 caracteres.");
        }
        AppUser user = new AppUser();
        user.setUsername(form.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setFirstName(form.getFirstName().trim());
        user.setLastName(form.getLastName().trim());
        user.setEmail(form.getEmail());
        user.setActive(form.isActive());
        if (form.isAdmin()) {
            user.getRoles().add(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        }
        if (form.isCashier()) {
            user.getRoles().add(roleRepository.findByName("ROLE_CAJERO").orElseThrow());
        }
        if (user.getRoles().isEmpty()) {
            throw new DomainException("Selecciona al menos un rol.");
        }
        userRepository.save(user);
    }
}
