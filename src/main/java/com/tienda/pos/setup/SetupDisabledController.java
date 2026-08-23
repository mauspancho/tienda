package com.tienda.pos.setup;

import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@NormalMode
public class SetupDisabledController {

    @GetMapping({"/setup", "/setup/**"})
    public String setupDisabled() {
        return "redirect:/";
    }
}