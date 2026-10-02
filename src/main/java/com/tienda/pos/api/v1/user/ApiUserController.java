package com.tienda.pos.api.v1.user;

import com.tienda.pos.api.v1.auth.ApiAuthModels.UserResponse;
import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import com.tienda.pos.user.UserForm;
import com.tienda.pos.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class ApiUserController {

    private final AppUserRepository repository;
    private final UserService service;

    public ApiUserController(AppUserRepository repository, UserService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public ApiPageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Page<AppUser> users = repository.findAll(PageRequest.of(Math.max(page, 0),
                Math.max(1, Math.min(size, 100)), Sort.by("username").ascending()));
        return ApiPageResponse.from(users, UserResponse::from);
    }

    @GetMapping("/{id}")
    public UserResponse detail(@PathVariable Long id) {
        return UserResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        return UserResponse.from(service.create(request.toForm()));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return UserResponse.from(service.update(id, request.toForm()));
    }

    @PatchMapping("/{id}/active")
    public UserResponse active(@PathVariable Long id, @RequestBody ActiveRequest request) {
        AppUser current = find(id);
        if (current.isActive() != request.active()) {
            current = service.toggleActive(id);
        }
        return UserResponse.from(current);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteOrDeactivate(id);
    }

    private AppUser find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiNotFoundException("Usuario no encontrado."));
    }

    public record UserRequest(@NotBlank String username, @Size(max = 128) String password,
                              @NotBlank String firstName, @NotBlank String lastName,
                              @Email String email, @NotEmpty List<String> roles, boolean active) {
        UserForm toForm() {
            List<String> normalized = roles.stream().map(String::toUpperCase).toList();
            UserForm form = new UserForm();
            form.setUsername(username);
            form.setPassword(password);
            form.setFirstName(firstName);
            form.setLastName(lastName);
            form.setEmail(email);
            form.setAdmin(normalized.contains("ADMIN") || normalized.contains("ROLE_ADMIN"));
            form.setCashier(normalized.contains("CAJERO") || normalized.contains("ROLE_CAJERO"));
            form.setActive(active);
            return form;
        }
    }

    public record ActiveRequest(boolean active) {
    }
}
