package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.Model.BusinessUser;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessMembershipDTO {

    private Long businessUserId;
    private Long businessId;
    private String businessName;
    private String businessCode;
    private String businessEmail;
    private String businessLogo;
    private UserRoles role;
    /** What this role allows (drives the app's menus and buttons; the server checks the same list). */
    private java.util.Set<com.ultimate.inventory.Constants.Capability> capabilities;

    public BusinessMembershipDTO(BusinessUser bu) {
        this.businessUserId = bu.getId();
        this.businessId = bu.getBusiness().getId();
        this.businessName = bu.getBusiness().getName();
        this.businessCode = bu.getBusiness().getCode();
        this.businessEmail = bu.getBusiness().getBusinessEmail();
        this.businessLogo = bu.getBusiness().getBusinessLogo();
        this.role = bu.getRoles();
        this.capabilities = com.ultimate.inventory.Constants.Capability.of(bu.getRoles());
    }
}
