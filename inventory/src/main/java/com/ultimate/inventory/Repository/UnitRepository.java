package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByBusiness(Business business);
    Optional<Unit> findByIdAndBusiness(Long id, Business business);
    boolean existsByBusinessAndNameIgnoreCase(Business business, String name);
}
