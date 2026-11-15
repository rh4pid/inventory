package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    List<Feature> findByBusinessAndDeletedFalseOrderByNameAsc(Business business);
    Optional<Feature> findByIdAndBusinessAndDeletedFalse(Long id, Business business);
    Optional<Feature> findFirstByBusinessAndNameIgnoreCaseAndDeletedFalse(Business business, String name);
}
