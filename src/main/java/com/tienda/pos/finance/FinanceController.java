package com.tienda.pos.finance;

import com.tienda.pos.common.NormalMode;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Controller
@NormalMode
@RequestMapping("/admin/finances")
@PreAuthorize("hasRole('ADMIN')")
public class FinanceController {

    private static final List<String> PERIODS = List.of("TODAY", "YESTERDAY", "LAST_7_DAYS", "LAST_30_DAYS", "THIS_MONTH", "PREVIOUS_MONTH", "THIS_YEAR", "CUSTOM");

    private final FinanceService financeService;
    private final CapitalMovementRepository capitalMovementRepository;

    public FinanceController(FinanceService financeService, CapitalMovementRepository capitalMovementRepository) {
        this.financeService = financeService;
        this.capitalMovementRepository = capitalMovementRepository;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "LAST_30_DAYS") String period,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(defaultValue = "profit") String productSort,
                        Model model) {
        model.addAttribute("summary", financeService.summary(period, from, to, productSort));
        model.addAttribute("periods", PERIODS);
        model.addAttribute("productSort", productSort);
        return "finances/index";
    }

    @GetMapping("/daily")
    public String daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate detailDate,
                        Model model) {
        LocalDate today = LocalDate.now();
        FinanceRange range = financeService.range("CUSTOM", from == null ? today.minusDays(29) : from, to == null ? today : to);
        model.addAttribute("range", range);
        model.addAttribute("days", financeService.daily(range.from(), range.to()));
        model.addAttribute("detailDate", detailDate);
        if (detailDate != null) {
            model.addAttribute("detail", financeService.detail(detailDate));
        }
        return "finances/daily";
    }

    @GetMapping(value = "/daily.csv", produces = "text/csv;charset=UTF-8")
    @ResponseBody
    public String dailyCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now();
        FinanceRange range = financeService.range("CUSTOM", from == null ? today.minusDays(29) : from, to == null ? today : to);
        StringBuilder csv = new StringBuilder("Fecha,Ventas,Costo vendido,Ganancia bruta,Gastos,Utilidad neta,Compras,Reinversion,Aportaciones,Retiros,Tickets,Unidades\n");
        for (DailyFinanceSummary day : financeService.daily(range.from(), range.to())) {
            csv.append(day.date()).append(',')
                    .append(number(day.sales())).append(',')
                    .append(number(day.costOfGoodsSold())).append(',')
                    .append(number(day.grossProfit())).append(',')
                    .append(number(day.expenses())).append(',')
                    .append(number(day.netProfit())).append(',')
                    .append(number(day.purchases())).append(',')
                    .append(number(day.reinvestment())).append(',')
                    .append(number(day.ownerContributions())).append(',')
                    .append(number(day.ownerWithdrawals())).append(',')
                    .append(day.tickets()).append(',')
                    .append(number(day.soldUnits())).append('\n');
        }
        return csv.toString();
    }

    @GetMapping("/capital")
    public String capital(@RequestParam(required = false) CapitalMovementType type,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        FinanceRange range = financeService.range("CUSTOM", from == null ? LocalDate.now().minusDays(89) : from, to == null ? LocalDate.now() : to);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), 20);
        Page<CapitalMovement> movements = type == null
                ? capitalMovementRepository.findByMovementDateBetweenOrderByMovementDateDesc(range.from(), range.to(), pageable)
                : capitalMovementRepository.findByTypeAndMovementDateBetweenOrderByMovementDateDesc(type, range.from(), range.to(), pageable);
        model.addAttribute("range", range);
        model.addAttribute("movements", movements);
        model.addAttribute("types", CapitalMovementType.values());
        model.addAttribute("selectedType", type);
        return "finances/capital";
    }

    @GetMapping("/capital/new")
    public String newCapital(Model model) {
        model.addAttribute("capitalMovementForm", new CapitalMovementForm());
        model.addAttribute("manualTypes", manualTypes());
        return "finances/capital-form";
    }

    @PostMapping("/capital")
    public String createCapital(@Valid @ModelAttribute CapitalMovementForm capitalMovementForm,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("manualTypes", manualTypes());
            return "finances/capital-form";
        }
        try {
            financeService.registerCapitalMovement(capitalMovementForm);
            redirectAttributes.addFlashAttribute("success", "Movimiento de capital registrado.");
            return "redirect:/admin/finances/capital";
        } catch (RuntimeException ex) {
            model.addAttribute("manualTypes", manualTypes());
            model.addAttribute("error", ex.getMessage());
            return "finances/capital-form";
        }
    }

    private List<CapitalMovementType> manualTypes() {
        return Arrays.stream(CapitalMovementType.values()).filter(CapitalMovementType::isManual).toList();
    }

    private String number(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }
}