package com.ultimate.inventory.Repository.Accounts;

import com.ultimate.inventory.Constants.JournalTransactionType;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JournalRepository extends JpaRepository<Journal, Long> {
    Page<Journal> findByBusiness(Business business, Pageable pageable);
    Page<Journal> findByShop(Shop shop, Pageable pageable);
    List<Journal> findByBusinessAndReferenceAndStatus(Business business, String reference,
                                                      com.ultimate.inventory.Constants.JournalStatus status);
    Optional<Journal> findBySourceIdAndTransactionType(Long sourceId, JournalTransactionType type);

    /** Journals filtered by type and date range [from, to), newest first. */
    @org.springframework.data.jpa.repository.Query("SELECT j FROM Journal j LEFT JOIN FETCH j.shop WHERE j.business = :business"
            + " AND (:type IS NULL OR j.transactionType = :type)"
            + " AND j.transactionDate >= :from AND j.transactionDate < :to")
    Page<Journal> search(@org.springframework.data.repository.query.Param("business") Business business,
                         @org.springframework.data.repository.query.Param("type") JournalTransactionType type,
                         @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                         @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
                         Pageable pageable);

    /** Journals that came from these invoices (any type), to see which invoices are already in the books. */
    List<Journal> findByBusinessAndSourceIdIn(Business business, java.util.Collection<Long> sourceIds);
}
