package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findByShopCode(String shopCode);
    Page<Shop> findByBusiness(Business business, Pageable pageable);

    @Query("SELECT s FROM Shop s WHERE s.business = :business AND (s.deleted IS NULL OR s.deleted = false)")
    Page<Shop> findActiveByBusiness(@Param("business") Business business, Pageable pageable);

    @Query("SELECT s FROM Shop s WHERE s.business = :business AND s.id IN :ids AND (s.deleted IS NULL OR s.deleted = false)")
    Page<Shop> findActiveByBusinessAndIdIn(@Param("business") Business business,
                                           @Param("ids") java.util.Collection<Long> ids, Pageable pageable);
}
