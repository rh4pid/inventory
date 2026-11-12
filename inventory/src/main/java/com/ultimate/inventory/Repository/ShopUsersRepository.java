package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.ShopUsers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopUsersRepository extends JpaRepository<ShopUsers, Long> {
    Page<ShopUsers> findByBusinessUser(BusinessUser businessUser, Pageable pageable);

    /** Active location assignments of a member. */
    @Query("SELECT su FROM ShopUsers su WHERE su.businessUser = :bu AND (su.deleted IS NULL OR su.deleted = false)")
    List<ShopUsers> findActiveByBusinessUser(@Param("bu") BusinessUser bu);

    /** Active staff assigned to a location. */
    @Query("SELECT su FROM ShopUsers su WHERE su.shop = :shop AND (su.deleted IS NULL OR su.deleted = false)")
    List<ShopUsers> findActiveByShop(@Param("shop") Shop shop);
}
