package com.tienda.pos.user;

import com.tienda.pos.common.NormalMode;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
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
        return "users/index";
    }

    @PostMapping("/users")
    public String create(@Valid @ModelAttribute UserForm form, BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisa los datos del usuario.");
            return "redirect:/users";
        }
        userService.create(form);
        redirectAttributes.addFlashAttribute("success", "Usuario creado correctamente.");
        return "redirect:/users";
    }
}
