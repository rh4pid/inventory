package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Page<StockMovement> findByShop(Shop shop, Pageable pageable);
    Page<StockMovement> findByProduct(Product product, Pageable pageable);
    Page<StockMovement> findByShop_Business(Business business, Pageable pageable);

    /** Movements written by one document (e.g. a sale), used to reverse it exactly. */
    java.util.List<StockMovement> findByReferenceCodeAndProductAndMovementTypeOrderByIdAsc(
            String referenceCode, Product product, com.ultimate.inventory.Constants.StockMovementType type);

    /** Movement history for a business, optionally narrowed to one location and/or product. */
    @Query("SELECT m FROM StockMovement m WHERE m.shop.business = :business " +
           "AND (:shop IS NULL OR m.shop = :shop) " +
           "AND (:product IS NULL OR m.product = :product) " +
           "ORDER BY m.createdAt DESC, m.id DESC")
    Page<StockMovement> search(@Param("business") Business business,
                               @Param("shop") Shop shop,
                               @Param("product") Product product,
                               Pageable pageable);

    /** Movement history limited to a set of locations (what a staff member may see). */
    @Query("SELECT m FROM StockMovement m WHERE m.shop.business = :business " +
           "AND m.shop.id IN :shopIds " +
           "AND (:product IS NULL OR m.product = :product) " +
           "ORDER BY m.createdAt DESC, m.id DESC")
    Page<StockMovement> searchInShops(@Param("business") Business business,
                                      @Param("shopIds") java.util.Collection<Long> shopIds,
                                      @Param("product") Product product,
                                      Pageable pageable);
}
