package com.tienda.pos.api.v1.whatsapp;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.whatsapp.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Mobile access uses the existing tenant-bound JWT, never provider secrets or automation credentials. */
@NormalMode
@RestController
@RequestMapping("/api/v1/whatsapp")
@PreAuthorize("hasRole('ADMIN')")
public class ApiWhatsappController {
    public record Connection(boolean providerConfigured, String providerName, String providerStatus,
                             String status, String phone, String lastCheckedAt, String error) { }
    public record Announcement(String status, boolean enabled, long availableGroups, long selectedGroups,
                               long selectedAvailableGroups, boolean ready) { }
    public record Group(Long id, String name, boolean available, boolean selectedForAnnouncements) { }
    public record State(Connection connection, Announcement announcements, List<Group> groups,
                        WhatsappGroupSyncService.Snapshot sync) { }
    public record Selection(@NotNull List<@NotNull Long> groupIds) { }
    public record Enabled(boolean enabled) { }
    public record PromotionRequest(@NotBlank @Size(max = 4000) String message, boolean sendToNumber, String phone) { }
    private final WhatsappIntegrationService service;
    private final WhatsappAnnouncementService announcements;
    private final WhatsappGroupSyncService sync;
    private final ManualWhatsappPromotionService promotions;

    public ApiWhatsappController(WhatsappIntegrationService service, WhatsappAnnouncementService announcements,
                                 WhatsappGroupSyncService sync, ManualWhatsappPromotionService promotions) {
        this.service = service; this.announcements = announcements; this.sync = sync; this.promotions = promotions;
    }
    @GetMapping
    public State state() {
        var data = service.dashboard();
        var c = data.connection() == null ? null : service.currentStatusCached();
        var p = data.provider();
        var a = announcements.current();
        return new State(new Connection(p != null && p.isEnabled(), p == null ? null : p.getName(),
                p == null ? null : p.getStatus().name(), c == null ? "NOT_CONFIGURED" : c.getStatus().name(),
                c == null ? null : c.getPhoneNumber(), c == null || c.getLastCheckedAt() == null ? null : c.getLastCheckedAt().toString(),
                c == null ? null : c.getLastError()),
                new Announcement(a.status().name(), a.settings() != null && a.settings().isEnabled(), a.availableGroups(),
                        a.selectedGroups(), a.selectedAvailableGroups(), a.ready()),
                announcements.currentGroups().stream().map(g -> new Group(g.getId(), g.getName(), g.isAvailable(), g.isSelectedForAnnouncements())).toList(), sync.current());
    }
    @PostMapping("/connect") public State connect() { service.connectCurrent(); return state(); }
    @PostMapping("/reconnect") public State reconnect() { service.reconnectCurrent(); return state(); }
    @PostMapping("/refresh") public State refresh() { service.refreshCurrentStatus(); return state(); }
    @PostMapping("/disassociate") public State disassociate() { service.disassociateCurrent(); return state(); }
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(service.qrCurrent()); }
    @PostMapping("/groups/sync") @ResponseStatus(HttpStatus.ACCEPTED)
    public WhatsappGroupSyncService.Snapshot startSync() { return sync.startCurrent(); }
    @GetMapping("/groups/sync") public WhatsappGroupSyncService.Snapshot sync() { return sync.current(); }
    @PutMapping("/announcements/groups") public State groups(@Valid @RequestBody Selection selection) { announcements.saveCurrentSelections(selection.groupIds()); return state(); }
    @PutMapping("/announcements/enabled") public State enabled(@RequestBody Enabled value) { announcements.setCurrentEnabled(value.enabled()); return state(); }
    @GetMapping("/products/{id}/preview")
    public ManualWhatsappPromotionService.Preview preview(@PathVariable Long id) { return promotions.preview(id); }
    @PostMapping("/products/{id}/promotions")
    public ManualWhatsappPromotionService.Result promote(@PathVariable Long id, @Valid @RequestBody PromotionRequest request,
                                                         @RequestHeader("Idempotency-Key") String key) {
        // The domain service durably records each recipient before sending and replays the same request key.
        return promotions.send(id, request.message(), key, request.sendToNumber(), request.phone());
    }
}
