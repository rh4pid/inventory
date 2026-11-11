package com.ultimate.inventory.Repository.Accounts;

import com.ultimate.inventory.Constants.JournalTransactionType;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JournalRepository extends JpaRepository<Journal, Long> {
    Page<Journal> findByBusiness(Business business, Pageable pageable);
    Page<Journal> findByShop(Shop shop, Pageable pageable);
    Optional<Journal> findBySourceIdAndTransactionType(Long sourceId, JournalTransactionType type);
}
