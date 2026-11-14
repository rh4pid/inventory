package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.StockCount;
import com.ultimate.inventory.Model.StockCountLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockCountLineRepository extends JpaRepository<StockCountLine, Long> {
    List<StockCountLine> findByStockCountOrderByIdAsc(StockCount count);
    boolean existsByStockCountAndProduct(StockCount count, Product product);
}
