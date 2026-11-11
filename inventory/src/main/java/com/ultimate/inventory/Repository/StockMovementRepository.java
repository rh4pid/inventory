package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Page<StockMovement> findByShop(Shop shop, Pageable pageable);
    Page<StockMovement> findByProduct(Product product, Pageable pageable);
    Page<StockMovement> findByShop_Business(Business business, Pageable pageable);
}
