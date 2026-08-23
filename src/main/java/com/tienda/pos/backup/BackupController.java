package com.tienda.pos.backup;

import com.tienda.pos.common.NormalMode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@NormalMode
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    @PostMapping("/settings/backup")
    public String backup(RedirectAttributes redirectAttributes) throws IOException {
        Files.createDirectories(Path.of("backups"));
        String name = "backup-nota-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
        Files.writeString(Path.of("backups", name),
                "Respaldo solicitado desde la aplicación.\n"
                        + "Instala mysqldump/mariadb-dump en el servidor para generar dumps completos desde línea de comandos.\n");
        redirectAttributes.addFlashAttribute("success", "Se creó una nota de respaldo en backups/. Si mysqldump está disponible, ejecútalo desde el servidor.");
        return "redirect:/settings";
    }
}
