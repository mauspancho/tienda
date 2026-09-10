package com.tienda.pos.auth;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.security.LoginDestination;
import com.tienda.pos.security.SessionLogoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
@NormalMode
public class RootController {
    private final SessionLogoutService logout;

    public RootController(SessionLogoutService logout) {
        this.logout = logout;
    }

    @GetMapping("/")
    public String index(Authentication authentication, HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        String destination = LoginDestination.forAuthentication(authentication);
        if (destination == null) {
            logout.redirect(request, response, "/admin/login");
            return null;
        }
        return "redirect:" + destination;
    }
}
