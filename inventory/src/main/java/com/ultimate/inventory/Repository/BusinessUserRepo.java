package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessUserRepo extends JpaRepository<BusinessUser, Long> {
    BusinessUser findByBusinessAndUser(Business business, User user);
    Page<BusinessUser> findByBusiness(Business business, Pageable pageable);
}
