package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Inventory;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.Shop;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductAndShop(Product product, Shop shop);

    List<Inventory> findByProduct(Product product);

    @Query("SELECT i FROM Inventory i WHERE i.shop.business = :business AND i.product.deleted = false")
    List<Inventory> findActiveByBusiness(@Param("business") Business business);

    /** Row-locks the stock line so two concurrent movements cannot both read the same "before" quantity. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product = :product AND i.shop = :shop")
    Optional<Inventory> findForUpdate(@Param("product") Product product, @Param("shop") Shop shop);

    Page<Inventory> findByShop(Shop shop, Pageable pageable);

    List<Inventory> findAllByShop(Shop shop);

    @Query("SELECT COALESCE(SUM(i.quantity), 0.0) FROM Inventory i WHERE i.product = :product")
    Double sumQuantityByProduct(@Param("product") Product product);

    @Query("SELECT i FROM Inventory i WHERE i.shop.business = :business " +
           "AND i.product.reorderLevel IS NOT NULL AND i.quantity <= i.product.reorderLevel " +
           "AND i.product.deleted = false")
    List<Inventory> findLowStockByBusiness(@Param("business") Business business);
}
