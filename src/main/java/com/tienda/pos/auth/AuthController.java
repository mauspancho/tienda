package com.tienda.pos.auth;

import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
public class AuthController {
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }
}
