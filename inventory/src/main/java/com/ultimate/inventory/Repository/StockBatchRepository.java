package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.StockBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    @Query("SELECT b FROM StockBatch b WHERE b.product = :product AND b.shop = :shop AND b.quantityRemaining > 0")
    List<StockBatch> findAvailable(@Param("product") Product product, @Param("shop") Shop shop);

    @Query("SELECT COALESCE(SUM(b.quantityRemaining), 0.0) FROM StockBatch b " +
           "WHERE b.product = :product AND b.shop = :shop")
    Double sumRemaining(@Param("product") Product product, @Param("shop") Shop shop);

    /** Batches with stock, optionally filtered by location and product. */
    @Query("SELECT b FROM StockBatch b WHERE b.shop.business = :business " +
           "AND (:shop IS NULL OR b.shop = :shop) AND (:product IS NULL OR b.product = :product) " +
           "AND (:includeEmpty = true OR b.quantityRemaining > 0) " +
           "ORDER BY b.product.productName, b.expiryDate, b.receivedAt")
    List<StockBatch> search(@Param("business") Business business, @Param("shop") Shop shop,
                            @Param("product") Product product, @Param("includeEmpty") boolean includeEmpty);

    /** Batches with stock that expire on or before {@code cutoff} (includes already expired). */
    @Query("SELECT b FROM StockBatch b WHERE b.shop.business = :business AND b.quantityRemaining > 0 " +
           "AND b.expiryDate IS NOT NULL AND b.expiryDate <= :cutoff " +
           "AND (:shop IS NULL OR b.shop = :shop) AND b.product.deleted = false " +
           "ORDER BY b.expiryDate, b.product.productName")
    List<StockBatch> findExpiring(@Param("business") Business business, @Param("shop") Shop shop,
                                  @Param("cutoff") LocalDate cutoff);
}
