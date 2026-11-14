package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.ProductUnit;
import com.ultimate.inventory.Model.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductUnitRepository extends JpaRepository<ProductUnit, Long> {
    List<ProductUnit> findByProduct(Product product);
    Optional<ProductUnit> findByProductAndUnit(Product product, Unit unit);
    boolean existsByUnit(Unit unit);
    void deleteByProduct(Product product);
}
