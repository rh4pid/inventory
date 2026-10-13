package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.SupplierProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {
}
