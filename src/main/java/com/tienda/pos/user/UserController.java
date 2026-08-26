package com.tienda.pos.user;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final AppUserRepository userRepository;
    private final UserService userService;

    public UserController(AppUserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping("/users")
    public String list(Model model) {
        model.addAttribute("users", userRepository.findAll(PageRequest.of(0, 100)));
        model.addAttribute("userForm", new UserForm());
        model.addAttribute("mode", "create");
        return "users/index";
    }

    @GetMapping("/users/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new DomainException("Usuario no encontrado."));
        model.addAttribute("users", userRepository.findAll(PageRequest.of(0, 100)));
        model.addAttribute("userForm", UserForm.from(user));
        model.addAttribute("mode", "edit");
        return "users/index";
    }

    @PostMapping("/users")
    public String create(@Valid @ModelAttribute UserForm form, BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos del usuario.");
            return "redirect:/admin/users";
        }
        try {
            userService.create(form);
            redirectAttributes.addFlashAttribute("success", "Usuario creado correctamente.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute UserForm form, BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos del usuario.");
            return "redirect:/admin/users/" + id + "/edit";
        }
        try {
            userService.update(id, form);
            redirectAttributes.addFlashAttribute("success", "Usuario actualizado correctamente.");
            return "redirect:/admin/users";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/users/" + id + "/edit";
        }
    }

    @PostMapping("/users/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.toggleActive(id);
            redirectAttributes.addFlashAttribute("success", "Estado del usuario actualizado.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteOrDeactivate(id);
            redirectAttributes.addFlashAttribute("success", "Usuario eliminado o desactivado si tenía historial.");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }
}
