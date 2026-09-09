package com.tienda.pos.commercial;

import com.tienda.pos.branch.Branch;
import com.tienda.pos.branch.BranchRepository;
import com.tienda.pos.business.Business;
import com.tienda.pos.business.BusinessRepository;
import com.tienda.pos.cash.CashRegister;
import com.tienda.pos.cash.CashRegisterRepository;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.settings.BusinessSettings;
import com.tienda.pos.settings.BusinessSettingsRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.warehouse.Warehouse;
import com.tienda.pos.warehouse.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreContextService {

    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final CurrentTenant currentTenant;

    public StoreContextService(BusinessRepository businessRepository, BranchRepository branchRepository,
                               WarehouseRepository warehouseRepository, CashRegisterRepository cashRegisterRepository,
                               BusinessSettingsRepository settingsRepository, CurrentTenant currentTenant) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.warehouseRepository = warehouseRepository;
        this.cashRegisterRepository = cashRegisterRepository;
        this.settingsRepository = settingsRepository;
        this.currentTenant = currentTenant;
    }

    @Transactional
    public Business defaultBusiness() {
        Tenant tenant = currentTenant.get();
        return businessRepository.findFirstByTenantIdAndActiveTrueOrderByIdAsc(tenant.getId())
                .orElseGet(() -> createDefaultBusiness(tenant));
    }

    @Transactional
    public Branch defaultBranch() {
        Tenant tenant = currentTenant.get();
        return branchRepository.findFirstByTenantIdAndActiveTrueOrderByIdAsc(tenant.getId())
                .orElseGet(() -> createDefaultBranch(tenant));
    }

    @Transactional
    public Warehouse defaultWarehouse() {
        Tenant tenant = currentTenant.get();
        return warehouseRepository.findFirstByTenantIdAndActiveTrueAndMainWarehouseTrueOrderByIdAsc(tenant.getId())
                .or(() -> warehouseRepository.findFirstByTenantIdAndActiveTrueOrderByIdAsc(tenant.getId()))
                .orElseGet(() -> createDefaultWarehouse(tenant));
    }

    @Transactional
    public CashRegister defaultCashRegister() {
        Tenant tenant = currentTenant.get();
        return cashRegisterRepository.findFirstByTenantIdAndActiveTrueOrderByIdAsc(tenant.getId())
                .orElseGet(() -> createDefaultCashRegister(tenant));
    }

    private Business createDefaultBusiness(Tenant tenant) {
        BusinessSettings settings = settings();
        Business business = new Business();
        business.setTenant(tenant);
        business.setName(settings.getStoreName() == null || settings.getStoreName().isBlank()
                ? tenant.getName()
                : settings.getStoreName().trim());
        business.setLegalName(business.getName());
        business.setTaxId(settings.getTaxId());
        business.setPhone(settings.getPhone());
        business.setActive(true);
        return businessRepository.save(business);
    }

    private Branch createDefaultBranch(Tenant tenant) {
        BusinessSettings settings = settings();
        Branch branch = new Branch();
        branch.setTenant(tenant);
        branch.setBusiness(defaultBusiness());
        branch.setCode("MATRIZ");
        branch.setName("Sucursal principal");
        branch.setAddress(settings.getAddress());
        branch.setPhone(settings.getPhone());
        branch.setTimezone(settings.getTimezone());
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    private Warehouse createDefaultWarehouse(Tenant tenant) {
        Warehouse warehouse = new Warehouse();
        warehouse.setTenant(tenant);
        warehouse.setBranch(defaultBranch());
        warehouse.setCode("PRINCIPAL");
        warehouse.setName("Almacén principal");
        warehouse.setMainWarehouse(true);
        warehouse.setActive(true);
        return warehouseRepository.save(warehouse);
    }

    private CashRegister createDefaultCashRegister(Tenant tenant) {
        CashRegister register = new CashRegister();
        register.setTenant(tenant);
        register.setBranch(defaultBranch());
        register.setCode("CAJA01");
        register.setName("Caja principal");
        register.setActive(true);
        return cashRegisterRepository.save(register);
    }

    private BusinessSettings settings() {
        Tenant tenant = currentTenant.get();
        return settingsRepository.findFirstByTenantIdOrderByIdAsc(tenant.getId())
                .orElseGet(() -> {
                    BusinessSettings created = new BusinessSettings();
                    created.setTenant(tenant);
                    created.setStoreName(tenant.getName());
                    return settingsRepository.save(created);
                });
    }

    public DomainException missingStructureException() {
        return new DomainException("No se encontró la estructura comercial principal.");
    }
}