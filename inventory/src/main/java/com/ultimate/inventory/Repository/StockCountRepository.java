package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Constants.StockCountStatus;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.StockCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, Long> {
    Optional<StockCount> findByIdAndBusiness(Long id, Business business);
    List<StockCount> findByBusinessOrderByCreatedAtDesc(Business business);
    boolean existsByShopAndStatusIn(Shop shop, Collection<StockCountStatus> statuses);
}
