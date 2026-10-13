package com.tienda.pos.credit;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.customer.*;
import com.tienda.pos.payment.PaymentMethod;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@NormalMode
@RequestMapping("/admin/credits")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class CreditController {
    private final com.tienda.pos.tenant.CurrentTenant currentTenant;
    private final com.tienda.pos.cash.CashRegisterSessionRepository sessions;
    private final com.tienda.pos.user.AppUserRepository users;
    private final CreditService creditService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    public CreditController(CreditService creditService, CustomerService customerService, CustomerRepository customerRepository, com.tienda.pos.tenant.CurrentTenant currentTenant, com.tienda.pos.cash.CashRegisterSessionRepository sessions, com.tienda.pos.user.AppUserRepository users) {
        this.currentTenant = currentTenant;
        this.sessions = sessions;
        this.users = users;
        this.creditService = creditService; this.customerService = customerService; this.customerRepository = customerRepository;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "DEBT") String filter,
                        @RequestParam(defaultValue = "DEBT") String sort, Model model) {
        model.addAttribute("summary", creditService.summary());
        model.addAttribute("customers", creditService.customerRows(q, filter, sort));
        model.addAttribute("q", q); model.addAttribute("filter", filter); model.addAttribute("sort", sort);
        return "credits/index";
    }

    @GetMapping("/customers/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newCustomer(Model model) { model.addAttribute("customerForm", new CustomerForm()); return "credits/customer-form"; }

    @PostMapping("/customers")
    @PreAuthorize("hasRole('ADMIN')")
    public String createCustomer(@Valid @ModelAttribute CustomerForm customerForm, BindingResult errors,
                                 RedirectAttributes redirect, Model model) {
        if (errors.hasErrors()) return "credits/customer-form";
        Customer customer = customerService.create(customerForm);
        redirect.addFlashAttribute("success", "Cliente registrado.");
        return "redirect:/admin/credits/customers/" + customer.getId();
    }

    @GetMapping("/customers/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editCustomer(@PathVariable Long id, Model model) {
        Customer customer = customerService.get(id);
        CustomerForm form = new CustomerForm(); form.setName(customer.getName()); form.setLastName(customer.getLastName());
        form.setPhone(customer.getPhone()); form.setNotes(customer.getNotes()); form.setActive(customer.isActive());
        model.addAttribute("customerForm", form); model.addAttribute("customerId", id);
        return "credits/customer-form";
    }

    @PostMapping("/customers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateCustomer(@PathVariable Long id, @Valid @ModelAttribute CustomerForm customerForm,
                                 BindingResult errors, RedirectAttributes redirect, Model model) {
        if (errors.hasErrors()) { model.addAttribute("customerId", id); return "credits/customer-form"; }
        customerService.update(id, customerForm); redirect.addFlashAttribute("success", "Cliente actualizado.");
        return "redirect:/admin/credits/customers/" + id;
    }

    @GetMapping("/customers/{id}")
    public String statement(@PathVariable Long id, Model model) {
        model.addAttribute("statement", creditService.statement(id));
        model.addAttribute("paymentForm", new CreditPaymentForm());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "credits/detail";
    }

    @GetMapping("/customers/{id}/balance")
    @ResponseBody
    public CreditBalance balance(@PathVariable Long id) {
        var statement = creditService.statement(id);
        return new CreditBalance(statement.balance(), statement.totalGranted(), statement.totalPaid());
    }

    @GetMapping("/customers/options")
    @ResponseBody
    public java.util.List<CustomerOption> customerOptions() {
        return customerRepository.findByTenantIdAndActiveTrueOrderByNameAscLastNameAsc(currentTenant.id()).stream()
                .filter(customer -> !"Público General".equalsIgnoreCase(customer.getName()))
                .map(customer -> new CustomerOption(customer.getId(), customer.getFullName())).toList();
    }

    @PostMapping("/customers/{id}/payments")
    public String payment(@PathVariable Long id, @Valid @ModelAttribute CreditPaymentForm paymentForm,
                          BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) { redirect.addFlashAttribute("error", "Captura un importe válido."); return "redirect:/admin/credits/customers/" + id; }
        creditService.registerPayment(id, paymentForm); redirect.addFlashAttribute("success", "Abono registrado.");
        return "redirect:/admin/credits/customers/" + id;
    }

    @PostMapping("/customers/{id}/charges")
    public String charge(@PathVariable Long id, @Valid @ModelAttribute CreditChargeForm chargeForm,
                         BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) { redirect.addFlashAttribute("error", "Captura un importe y concepto válidos."); return "redirect:/admin/credits/customers/" + id; }
        creditService.registerCharge(id, chargeForm); redirect.addFlashAttribute("success", "Cargo monetario agregado al crédito.");
        return "redirect:/admin/credits/customers/" + id;
    }

    @PostMapping("/sales/{saleId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public String cancel(@PathVariable Long saleId, @RequestParam Long customerId, @RequestParam String reason, RedirectAttributes redirect) {
        creditService.cancelCredit(saleId, reason); redirect.addFlashAttribute("success", "Crédito cancelado e inventario restaurado.");
        return "redirect:/admin/credits/customers/" + customerId;
    }

    @PostMapping("/payments/{paymentId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public String reverse(@PathVariable Long paymentId, @RequestParam Long customerId, @RequestParam String reason, RedirectAttributes redirect) {
        creditService.reversePayment(paymentId, reason); redirect.addFlashAttribute("success", "Abono revertido.");
        return "redirect:/admin/credits/customers/" + customerId;
    }

    @PostMapping("/charges/{chargeId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public String reverseCharge(@PathVariable Long chargeId, @RequestParam Long customerId,
                                @RequestParam String reason, RedirectAttributes redirect) {
        creditService.reverseCharge(chargeId, reason); redirect.addFlashAttribute("success", "Cargo monetario revertido.");
        return "redirect:/admin/credits/customers/" + customerId;
    }

    @GetMapping("/new")
    public String newCredit(@RequestParam(required = false) Long customerId, Model model) {
        model.addAttribute("customers", customerRepository.findByTenantIdAndActiveTrueOrderByNameAscLastNameAsc(currentTenant.id()).stream()
                .filter(c -> !"Público General".equalsIgnoreCase(c.getName())).toList());
        if (customerId != null) customerService.get(customerId);
        var user = users.findByUsernameWithTenant(com.tienda.pos.common.CurrentUser.username()).orElseThrow();
        model.addAttribute("cashOpen", sessions.findByTenantIdAndCashierAndOpenTrue(currentTenant.id(), user).isPresent());
        model.addAttribute("selectedCustomerId", customerId);
        return "credits/new";
    }

    public record CreditBalance(java.math.BigDecimal balance, java.math.BigDecimal totalGranted,
                                java.math.BigDecimal totalPaid) {}
    public record CustomerOption(Long id, String name) {}
}
