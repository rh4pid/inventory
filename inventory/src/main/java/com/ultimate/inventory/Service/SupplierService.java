package com.ultimate.inventory.Service;

import com.ultimate.inventory.DTO.SupplierDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Supplier;
import com.ultimate.inventory.Repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class SupplierService {

    private static final Logger log = LoggerFactory.getLogger(SupplierService.class);

    private final SupplierRepository supplierRepo;
    private final BusinessAccessHelper accessHelper;

    public SupplierService(SupplierRepository supplierRepo, BusinessAccessHelper accessHelper) {
        this.supplierRepo = supplierRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Supplier createSupplier(Long businessId, SupplierDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Supplier supplier = new Supplier();
        supplier.setBusiness(business);
        supplier.setCreatedBy(userEmail);
        supplier.setCreatedAt(new Date());
        applyDto(supplier, dto);

        Supplier saved = supplierRepo.save(supplier);
        log.info("Supplier created: {} by {}", saved.getId(), userEmail);
        return saved;
    }

    public Page<Supplier> listSuppliers(Long businessId, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return supplierRepo.findByBusinessAndDeletedFalse(business, pageable);
    }

    public Supplier getSupplier(Long businessId, Long supplierId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return supplierRepo.findByIdAndBusinessAndDeletedFalse(supplierId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Supplier not found"));
    }

    @Transactional
    public Supplier updateSupplier(Long businessId, Long supplierId, SupplierDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Supplier supplier = supplierRepo.findByIdAndBusinessAndDeletedFalse(supplierId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Supplier not found"));

        supplier.setUpdatedBy(userEmail);
        supplier.setUpdatedAt(new Date());
        applyDto(supplier, dto);
        return supplierRepo.save(supplier);
    }

    @Transactional
    public void deleteSupplier(Long businessId, Long supplierId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Supplier supplier = supplierRepo.findByIdAndBusinessAndDeletedFalse(supplierId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Supplier not found"));

        supplier.setDeleted(true);
        supplier.setUpdatedBy(userEmail);
        supplier.setUpdatedAt(new Date());
        supplierRepo.save(supplier);
        log.info("Supplier soft-deleted: {} by {}", supplierId, userEmail);
    }

    private void applyDto(Supplier supplier, SupplierDTO dto) {
        supplier.setCompanyName(dto.getCompanyName());
        supplier.setRelationshipOfficer(dto.getRelationshipOfficer());
        supplier.setCompanyEmail(dto.getCompanyEmail());
        supplier.setRmEmail(dto.getRmEmail());
        supplier.setTelephonePrimary(dto.getTelephonePrimary());
        supplier.setTelephoneSecondary(dto.getTelephoneSecondary());
    }
}
