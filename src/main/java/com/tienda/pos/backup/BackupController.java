package com.tienda.pos.backup;

import com.tienda.pos.common.NormalMode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@NormalMode
@org.springframework.web.bind.annotation.RequestMapping("/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class BackupController {

    @PostMapping("/settings/backup")
    public String backup() {
        return "redirect:/platform/tenants";
    }
}
