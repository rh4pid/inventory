package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessLogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessLogoRepository extends JpaRepository<BusinessLogo, Long> {
    Optional<BusinessLogo> findByBusiness(Business business);
    Optional<BusinessLogo> findByImageKey(String imageKey);
}
