package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByBusinessAndDeletedFalse(Business business, Pageable pageable);

    long countByBusinessAndDeletedFalse(Business business);

    /** Name, SKU or barcode contains the term (case-insensitive): what a till or picker types or scans. */
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Product p WHERE p.business = :business AND p.deleted = false AND (" +
           "LOWER(p.productName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.barcode = :q)")
    Page<Product> search(@org.springframework.data.repository.query.Param("business") Business business,
                         @org.springframework.data.repository.query.Param("q") String q, Pageable pageable);
    Optional<Product> findByIdAndBusinessAndDeletedFalse(Long id, Business business);
    Optional<Product> findByBusinessAndSku(Business business, String sku);
    Page<Product> findByBusinessAndProductNameContainingIgnoreCaseAndDeletedFalse(
            Business business, String productName, Pageable pageable);
    boolean existsByUnit(Unit unit);
}
