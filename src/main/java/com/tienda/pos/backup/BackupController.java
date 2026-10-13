package com.tienda.pos.backup;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final StoreBackupService backupService;

    public BackupController(StoreBackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/backup")
    public String page(Model model) {
        model.addAttribute("backupInfo", backupService.info());
        return "backup/index";
    }

    @GetMapping("/backup/export")
    public ResponseEntity<StreamingResponseBody> export() {
        StreamingResponseBody body = backupService::exportArchive;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + backupService.exportFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @PostMapping("/backup/import")
    public String importArchive(@RequestParam("backupFile") MultipartFile backupFile,
                                @RequestParam(defaultValue = "") String confirmation,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        if (!"IMPORTAR".equals(confirmation.trim())) {
            redirectAttributes.addFlashAttribute("error", "Escribe IMPORTAR para confirmar la restauración.");
            return "redirect:/admin/backup";
        }
        try {
            backupService.importArchive(backupFile);
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return "redirect:/admin/login?restored";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/backup";
        }
    }

    @PostMapping("/settings/backup")
    public String legacyBackup() {
        return "redirect:/admin/backup";
    }
}
