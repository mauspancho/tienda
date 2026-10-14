package com.tienda.pos.whatsapp;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.*;

@Controller
@NormalMode
@RequestMapping("/admin/integrations/whatsapp")
@PreAuthorize("hasRole('ADMIN')")
public class WhatsappAdminController {
    private final WhatsappIntegrationService service;
    private final WhatsappAnnouncementService announcements;
    private final CurrentTenant currentTenant;
    public WhatsappAdminController(WhatsappIntegrationService service, WhatsappAnnouncementService announcements,
                                   CurrentTenant currentTenant) {
        this.service = service; this.announcements = announcements; this.currentTenant = currentTenant;
    }

    @GetMapping
    public String page(Model model) {
        var data = service.dashboard();
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                + "/api/integrations/v1/" + data.tenant().getCode() + "/whatsapp";
        model.addAttribute("data", data); model.addAttribute("apiBase", base);
        model.addAttribute("announcements", announcements.current());
        model.addAttribute("announcementGroups", announcements.currentGroups());
        return "whatsapp/index";
    }

    @PostMapping("/connect")
    public String connect(RedirectAttributes flash) { return action(flash, () -> service.connectCurrent(), "Sesión iniciada. Escanea el QR si se solicita."); }
    @PostMapping("/status")
    public String refresh(RedirectAttributes flash) { return action(flash, () -> service.refreshCurrentStatus(), "Estado actualizado."); }
    @PostMapping("/reconnect")
    public String reconnect(RedirectAttributes flash) { return action(flash, () -> service.reconnectCurrent(), "Reconexión solicitada sin reemplazar la sesión."); }
    @PostMapping("/groups")
    public String groups(RedirectAttributes flash) { return action(flash, () -> service.refreshCurrentGroups(), "Grupos actualizados."); }
    @PostMapping("/disassociate")
    public String disassociate(RedirectAttributes flash) { return action(flash, () -> { service.disassociateCurrent(); return null; }, "Asociación local eliminada. La sesión de WPPConnect permanece intacta."); }

    @PostMapping("/announcements/groups")
    public String announcementGroups(@RequestParam(name = "announcementGroupIds", required = false) List<Long> groupIds,
                                     RedirectAttributes flash) {
        return action(flash, () -> announcements.saveCurrentSelections(groupIds == null ? List.of() : groupIds),
                "Grupos para anuncios guardados.");
    }

    @PostMapping("/announcements/enabled")
    public String announcementsEnabled(@RequestParam(defaultValue = "false") boolean enabled,
                                       RedirectAttributes flash) {
        return action(flash, () -> announcements.setCurrentEnabled(enabled),
                enabled ? "Anuncios automáticos habilitados." : "Anuncios automáticos deshabilitados; los grupos se conservaron.");
    }

    @GetMapping(value = "/announcements/status.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String,Object> announcementStatus() {
        var value = announcements.current();
        return Map.of("status", value.status().name(), "enabled", value.settings() != null && value.settings().isEnabled(),
                "availableGroups", value.availableGroups(), "selectedGroups", value.selectedGroups(),
                "selectedAvailableGroups", value.selectedAvailableGroups(), "ready", value.ready());
    }

    @GetMapping(value = "/announcements/groups.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String,Object> announcementGroups() {
        return Map.of("groups", announcements.currentGroups().stream().map(group -> Map.of(
                "id", group.getId(), "whatsappId", group.getWhatsappId(), "name", group.getName(),
                "available", group.isAvailable(), "selectedForAnnouncements", group.isSelectedForAnnouncements())).toList());
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qr() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(service.qrCurrent());
    }

    @GetMapping(value = "/status.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String,Object> statusJson() {
        var connection = service.dashboard().connection();
        if (connection != null) connection = service.refreshCurrentStatus();
        return Map.of("status", connection == null ? "NOT_CONFIGURED" : connection.getStatus().name(),
                "phone", connection == null || connection.getPhoneNumber() == null ? "" : connection.getPhoneNumber());
    }

    @PostMapping("/tokens")
    public String token(@RequestParam(defaultValue = "n8n") String name,
                        @RequestParam(required = false) List<IntegrationScope> scopes, RedirectAttributes flash) {
        try {
            var generated = service.generateToken(name, scopes == null ? Set.of() : Set.copyOf(scopes));
            flash.addFlashAttribute("newToken", generated.value()); flash.addFlashAttribute("success", "Credencial generada. Cópiala ahora: no volverá a mostrarse.");
        } catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return redirect();
    }

    @PostMapping("/tokens/{id}/revoke")
    public String revoke(@PathVariable Long id, RedirectAttributes flash) {
        return action(flash, () -> { service.revokeCurrentToken(id); return null; }, "Credencial revocada.");
    }

    @PostMapping("/test")
    public String test(@RequestParam String phone, @RequestParam(defaultValue = "false") boolean isGroup,
                       @RequestParam String message, RedirectAttributes flash) {
        return action(flash, () -> service.sendText(currentTenant.get(), phone, isGroup, message), "Mensaje enviado.");
    }

    private String action(RedirectAttributes flash, java.util.concurrent.Callable<?> action, String success) {
        try { action.call(); flash.addFlashAttribute("success", success); }
        catch (Exception ex) { flash.addFlashAttribute("error", ex instanceof DomainException || ex instanceof WppConnectException ? ex.getMessage() : "No fue posible completar la operación."); }
        return redirect();
    }
    private String redirect() { return "redirect:/admin/integrations/whatsapp"; }
}
