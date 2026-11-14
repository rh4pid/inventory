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

// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class SettingsService {

    /** Theme ids the app knows (kept in step with inventory-front/src/lib/themes.ts). */
    public static final java.util.Set<String> THEMES = java.util.Set.of("market", "care", "fresh", "depot", "workshop", "bistro");

    private final BusinessSettingsRepository settingsRepo;
    private final BusinessAccessHelper accessHelper;

    private final com.ultimate.inventory.Repository.BusinessRepository businessRepo;

    public SettingsService(BusinessSettingsRepository settingsRepo, BusinessAccessHelper accessHelper,
                           com.ultimate.inventory.Repository.BusinessRepository businessRepo) {
        this.settingsRepo = settingsRepo;
        this.accessHelper = accessHelper;
        this.businessRepo = businessRepo;
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
        if (dto.getTheme() != null) {
            String t = dto.getTheme().trim().toLowerCase();
            if (t.isEmpty() || t.equals("auto")) s.setTheme(null);
            else if (THEMES.contains(t)) s.setTheme(t);
            else throw new com.ultimate.inventory.Exception.AppException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown theme: " + t);
        }
        // Expiry dates live on batches: switching batches off switches expiry off,
        // and switching expiry on switches batches on.
        if (Boolean.FALSE.equals(dto.getTrackBatches())) s.setTrackExpiry(false);
        else if (Boolean.TRUE.equals(s.getTrackExpiry())) s.setTrackBatches(true);

        s.setUpdatedBy(userEmail);
        s.setUpdatedAt(new Date());
        return settingsRepo.save(s);
    }

    public com.ultimate.inventory.Controller.SettingsController.BusinessProfile getProfile(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        return profile(accessHelper.requireBusiness(businessId));
    }

    @Transactional
    public com.ultimate.inventory.Controller.SettingsController.BusinessProfile updateProfile(
            Long businessId, com.ultimate.inventory.Controller.SettingsController.BusinessProfile body, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, com.ultimate.inventory.Constants.Capability.MANAGE_SETTINGS);
        Business b = accessHelper.requireBusiness(businessId);
        if (body.name() == null || body.name().isBlank()) {
            throw new com.ultimate.inventory.Exception.AppException(org.springframework.http.HttpStatus.BAD_REQUEST, "Business name is required");
        }
        String newEmail = body.email() == null || body.email().isBlank() ? null : UserAccounts.normalizeEmail(body.email());
        if (newEmail != null) {
            Business other = businessRepo.findByBusinessEmail(newEmail);
            if (other != null && !other.getId().equals(b.getId())) {
                throw new com.ultimate.inventory.Exception.AppException(org.springframework.http.HttpStatus.CONFLICT, "Another company already uses " + newEmail);
            }
        }
        b.setName(body.name().trim());
        b.setBusinessEmail(newEmail);
        b.setBusinessContact(blank(body.contact()));
        b.setBusinessSecondaryContact(blank(body.secondaryContact()));
        b.setUpdatedBy(userEmail);
        b.setUpdatedAt(new Date());
        return profile(businessRepo.save(b));
    }

    private static com.ultimate.inventory.Controller.SettingsController.BusinessProfile profile(Business b) {
        return new com.ultimate.inventory.Controller.SettingsController.BusinessProfile(b.getId(), b.getName(), b.getCode(),
                b.getBusinessEmail(), b.getBusinessContact(), b.getBusinessSecondaryContact(), b.getBusinessLogo());
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

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
