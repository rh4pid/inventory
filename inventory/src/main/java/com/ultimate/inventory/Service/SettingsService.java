package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.BusinessType;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.BusinessSettingsDTO;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessSettings;
import com.ultimate.inventory.Repository.BusinessSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class SettingsService {

    private final BusinessSettingsRepository settingsRepo;
    private final BusinessAccessHelper accessHelper;

    public SettingsService(BusinessSettingsRepository settingsRepo, BusinessAccessHelper accessHelper) {
        this.settingsRepo = settingsRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public BusinessSettings getSettings(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        return forBusiness(accessHelper.requireBusiness(businessId));
    }

    @Transactional
    public BusinessSettings updateSettings(Long businessId, BusinessSettingsDTO dto, String userEmail) {
        accessHelper.requireRole(businessId, userEmail, UserRoles.OWNER, UserRoles.ADMIN);
        BusinessSettings s = forBusiness(accessHelper.requireBusiness(businessId));

        if (dto.getBusinessType() != null) {
            if (Boolean.TRUE.equals(dto.getApplyPreset())) s.applyPreset(dto.getBusinessType());
            else s.setBusinessType(dto.getBusinessType());
        }
        if (dto.getCurrencyCode() != null) s.setCurrencyCode(dto.getCurrencyCode());
        if (dto.getCurrencySymbol() != null) s.setCurrencySymbol(dto.getCurrencySymbol());
        if (dto.getTaxEnabled() != null) s.setTaxEnabled(dto.getTaxEnabled());
        if (dto.getVatNumber() != null) s.setVatNumber(dto.getVatNumber());
        if (dto.getLowStockAlerts() != null) s.setLowStockAlerts(dto.getLowStockAlerts());
        if (dto.getTrackBatches() != null) s.setTrackBatches(dto.getTrackBatches());
        if (dto.getTrackExpiry() != null) s.setTrackExpiry(dto.getTrackExpiry());
        if (dto.getExpiryAlertDays() != null) s.setExpiryAlertDays(dto.getExpiryAlertDays());
        if (dto.getMultipleUnits() != null) s.setMultipleUnits(dto.getMultipleUnits());
        if (dto.getAllowNegativeStock() != null) s.setAllowNegativeStock(dto.getAllowNegativeStock());
        if (dto.getRequireAdjustmentApproval() != null) s.setRequireAdjustmentApproval(dto.getRequireAdjustmentApproval());
        // Expiry dates live on batches: switching batches off switches expiry off,
        // and switching expiry on switches batches on.
        if (Boolean.FALSE.equals(dto.getTrackBatches())) s.setTrackExpiry(false);
        else if (Boolean.TRUE.equals(s.getTrackExpiry())) s.setTrackBatches(true);

        s.setUpdatedBy(userEmail);
        s.setUpdatedAt(new Date());
        return settingsRepo.save(s);
    }

    /** Settings for a business, created with defaults on first access. Performs no access check. */
    @Transactional
    public BusinessSettings forBusiness(Business business) {
        return settingsRepo.findByBusiness(business).orElseGet(() -> {
            BusinessSettings s = new BusinessSettings();
            s.setBusiness(business);
            s.applyPreset(BusinessType.GENERAL_RETAIL);
            s.setCreatedAt(new Date());
            s.setCreatedBy("system");
            return settingsRepo.save(s);
        });
    }

    /** Sets a new company's business type with its feature preset (no access check: used by the super admin). */
    @Transactional
    public BusinessSettings applyBusinessType(Business business, BusinessType type, String by) {
        BusinessSettings s = forBusiness(business);
        if (type != null) s.applyPreset(type);
        s.setUpdatedBy(by);
        s.setUpdatedAt(new Date());
        return settingsRepo.save(s);
    }

    public boolean allowNegativeStock(Business business) {
        return Boolean.TRUE.equals(forBusiness(business).getAllowNegativeStock());
    }
}
