package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.Controller.SettingsController;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessLogo;
import com.ultimate.inventory.Repository.BusinessLogoRepository;
import com.ultimate.inventory.Repository.BusinessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/** Company logos: owners and admins upload (replacing the old one) or remove; anyone can view by key. */
@Service
public class BusinessLogoService {

    static final long MAX_BYTES = 1_500_000;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BusinessLogoRepository logoRepo;
    private final BusinessRepository businessRepo;
    private final BusinessAccessHelper accessHelper;
    private final SettingsService settingsService;

    public BusinessLogoService(BusinessLogoRepository logoRepo, BusinessRepository businessRepo,
                               BusinessAccessHelper accessHelper, SettingsService settingsService) {
        this.logoRepo = logoRepo;
        this.businessRepo = businessRepo;
        this.accessHelper = accessHelper;
        this.settingsService = settingsService;
    }

    @Transactional
    public SettingsController.BusinessProfile upload(Long businessId, MultipartFile file, String email) {
        Business business = requireBusiness(businessId, email);
        if (file == null || file.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Choose a picture");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TYPES.contains(type)) throw new AppException(HttpStatus.BAD_REQUEST, "Use a JPG, PNG or WebP picture");
        if (file.getSize() > MAX_BYTES) throw new AppException(HttpStatus.BAD_REQUEST, "The picture is too big (1.5 MB at most)");

        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw new AppException(HttpStatus.BAD_REQUEST, "Couldn't read the picture"); }

        BusinessLogo logo = logoRepo.findByBusiness(business).orElseGet(() -> {
            BusinessLogo n = new BusinessLogo();
            n.setBusiness(business);
            n.setCreatedBy(email);
            n.setCreatedAt(new Date());
            return n;
        });
        logo.setImageKey(newKey()); // a new link each time, so browsers never show the old logo
        logo.setContentType(type);
        logo.setData(bytes);
        logo.setUpdatedBy(email);
        logo.setUpdatedAt(new Date());
        logoRepo.save(logo);

        business.setBusinessLogo(logo.getImageKey());
        business.setUpdatedBy(email);
        business.setUpdatedAt(new Date());
        businessRepo.save(business);
        return settingsService.getProfile(businessId, email);
    }

    @Transactional
    public SettingsController.BusinessProfile remove(Long businessId, String email) {
        Business business = requireBusiness(businessId, email);
        logoRepo.findByBusiness(business).ifPresent(logoRepo::delete);
        business.setBusinessLogo(null);
        business.setUpdatedBy(email);
        business.setUpdatedAt(new Date());
        businessRepo.save(business);
        return settingsService.getProfile(businessId, email);
    }

    @Transactional(readOnly = true)
    public Optional<BusinessLogo> byKey(String key) {
        if (key == null || !key.matches("[0-9a-f]{32}")) return Optional.empty();
        return logoRepo.findByImageKey(key).map(l -> { l.getData(); return l; });
    }

    private Business requireBusiness(Long businessId, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_SETTINGS);
        return accessHelper.requireBusiness(businessId);
    }

    private static String newKey() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
