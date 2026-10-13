package com.tienda.pos.backup;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.platform.PlatformTenantService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
@NormalMode
@RequestMapping("/platform/tenants/{id}/backup")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformBackupController {
    private final PlatformTenantService tenants;
    private final TenantBackupService backups;

    public PlatformBackupController(PlatformTenantService tenants, TenantBackupService backups) {
        this.tenants = tenants; this.backups = backups;
    }

    @GetMapping
    public String page(@PathVariable long id, Model model) {
        model.addAttribute("tenant", tenants.detail(id).tenant());
        return "platform/backup";
    }

    @GetMapping("/export")
    public void export(@PathVariable long id, HttpServletResponse response) throws IOException {
        Path zip = Files.createTempFile("tenant-export-", ".zip");
        try {
            try (var out = Files.newOutputStream(zip)) { backups.exportArchive(id, out); }
            response.setContentType("application/zip");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Content-Disposition", "attachment; filename=\"tienda-" + id + "-respaldo.zip\"");
            response.setContentLengthLong(Files.size(zip));
            Files.copy(zip, response.getOutputStream());
        } finally { Files.deleteIfExists(zip); }
    }

    @PostMapping("/import")
    public String restore(@PathVariable long id, @RequestParam MultipartFile backupFile,
                          @RequestParam(defaultValue = "") String confirmation, RedirectAttributes flash) {
        var tenant = tenants.detail(id).tenant();
        if (!("IMPORTAR " + tenant.code()).equals(confirmation.trim())) {
            flash.addFlashAttribute("error", "Escribe IMPORTAR seguido del codigo de esta tienda para confirmar.");
        } else {
            try {
                backups.importArchive(id, backupFile);
                flash.addFlashAttribute("success", "Respaldo importado exclusivamente en " + tenant.name()
                        + ". La tienda sigue suspendida; revisa los datos y los usuarios antes de reactivarla.");
            } catch (DomainException ex) {
                flash.addFlashAttribute("error", ex.getMessage());
            } catch (IOException ex) {
                flash.addFlashAttribute("error", "No se pudo leer el ZIP o sus imagenes. No se aplicaron cambios.");
            }
        }
        return "redirect:/platform/tenants/" + id + "/backup";
    }
}
