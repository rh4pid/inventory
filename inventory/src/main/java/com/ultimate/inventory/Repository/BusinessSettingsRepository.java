package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Long> {
    Optional<BusinessSettings> findByBusiness(Business business);
}
