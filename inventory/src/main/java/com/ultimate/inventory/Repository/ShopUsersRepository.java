package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.ShopUsers;
import com.ultimate.inventory.Model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopUsersRepository extends JpaRepository<ShopUsers, Long> {
    //ShopUsers findByUser(User user);
    Page<ShopUsers> findByBusinessUser(BusinessUser businessUser, Pageable pageable);
}
