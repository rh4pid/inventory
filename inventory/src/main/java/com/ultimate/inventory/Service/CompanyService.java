package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.BusinessType;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.BusinessMembershipDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/** Companies people create for themselves. The creator becomes the owner. */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final BusinessAccessHelper accessHelper;
    private final BusinessUserRepo businessUserRepo;
    private final CompanySetup companySetup;
    /** Stops one account from creating companies endlessly. */
    private final int maxOwned;

    public CompanyService(BusinessAccessHelper accessHelper, BusinessUserRepo businessUserRepo, CompanySetup companySetup,
                          @Value("${app.max-companies-per-owner:10}") int maxOwned) {
        this.accessHelper = accessHelper;
        this.businessUserRepo = businessUserRepo;
        this.companySetup = companySetup;
        this.maxOwned = maxOwned;
    }

    public record NewCompany(String name, BusinessType businessType, String email, String contact) {}

    @Transactional
    public BusinessMembershipDTO create(NewCompany req, String email) {
        User user = accessHelper.requireUser(email);
        if (Boolean.TRUE.equals(user.getSuperAdmin())) {
            throw new AppException(HttpStatus.FORBIDDEN, "The system administrator creates companies from the admin console");
        }
        if (req == null) throw new AppException(HttpStatus.BAD_REQUEST, "Company name is required");
        long owned = businessUserRepo.findByUser(user).stream()
                .filter(BusinessUser::isActiveMember)
                .filter(bu -> bu.getRoles() == UserRoles.OWNER)
                .filter(bu -> !Boolean.TRUE.equals(bu.getBusiness().getDeleted()))
                .count();
        if (owned >= maxOwned) {
            throw new AppException(HttpStatus.CONFLICT, "You already own " + owned + " companies, the most allowed. Contact support for more.");
        }

        Business b = companySetup.create(req.name(), req.email(), req.contact(), req.businessType(), email);
        BusinessUser bu = new BusinessUser();
        bu.setBusiness(b);
        bu.setUser(user);
        bu.setRoles(UserRoles.OWNER);
        bu.setActive(true);
        bu.setCreatedBy(email);
        bu.setCreatedAt(new Date());
        bu = businessUserRepo.save(bu);

        log.info("Company {} ({}) created by its owner {}", b.getCode(), b.getName(), email);
        return new BusinessMembershipDTO(bu);
    }
}
