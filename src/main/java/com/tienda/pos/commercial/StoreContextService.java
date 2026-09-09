package com.tienda.pos.commercial;

import com.tienda.pos.branch.Branch;
import com.tienda.pos.branch.BranchRepository;
import com.tienda.pos.business.Business;
import com.tienda.pos.business.BusinessRepository;
import com.tienda.pos.cash.CashRegister;
import com.tienda.pos.cash.CashRegisterRepository;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.settings.BusinessSettingsRepository;
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

    public StoreContextService(BusinessRepository businessRepository, BranchRepository branchRepository,
                               WarehouseRepository warehouseRepository, CashRegisterRepository cashRegisterRepository,
                               BusinessSettingsRepository settingsRepository) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.warehouseRepository = warehouseRepository;
        this.cashRegisterRepository = cashRegisterRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional
    public Business defaultBusiness() {
        return businessRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(this::createDefaultBusiness);
    }

    @Transactional
    public Branch defaultBranch() {
        return branchRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(this::createDefaultBranch);
    }

    @Transactional
    public Warehouse defaultWarehouse() {
        return warehouseRepository.findFirstByActiveTrueAndMainWarehouseTrueOrderByIdAsc()
                .or(() -> warehouseRepository.findFirstByActiveTrueOrderByIdAsc())
                .orElseGet(this::createDefaultWarehouse);
    }

    @Transactional
    public CashRegister defaultCashRegister() {
        return cashRegisterRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(this::createDefaultCashRegister);
    }

    private Business createDefaultBusiness() {
        Business business = new Business();
        business.setName(settingsRepository.findById(1L)
                .map(settings -> settings.getStoreName() == null || settings.getStoreName().isBlank()
                        ? "Tienda POS"
                        : settings.getStoreName().trim())
                .orElse("Tienda POS"));
        business.setTaxId(settingsRepository.findById(1L).map(settings -> settings.getTaxId()).orElse(null));
        business.setActive(true);
        return businessRepository.save(business);
    }

    private Branch createDefaultBranch() {
        Branch branch = new Branch();
        branch.setBusiness(defaultBusiness());
        branch.setName("Sucursal principal");
        branch.setAddress(settingsRepository.findById(1L).map(settings -> settings.getAddress()).orElse(null));
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    private Warehouse createDefaultWarehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(defaultBranch());
        warehouse.setName("Almacén principal");
        warehouse.setMainWarehouse(true);
        warehouse.setActive(true);
        return warehouseRepository.save(warehouse);
    }

    private CashRegister createDefaultCashRegister() {
        CashRegister register = new CashRegister();
        register.setBranch(defaultBranch());
        register.setName("Caja principal");
        register.setActive(true);
        return cashRegisterRepository.save(register);
    }

    public DomainException missingStructureException() {
        return new DomainException("No se encontró la estructura comercial principal.");
    }
}
