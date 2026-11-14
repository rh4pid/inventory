package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /** Expenses filtered by status, location and/or the person who recorded them (null = any). */
    @EntityGraph(attributePaths = {"shop", "expenseAccount", "paidFrom"})
    @Query("SELECT e FROM Expense e WHERE e.business = :business"
            + " AND (:status IS NULL OR e.status = :status)"
            + " AND (:shopId IS NULL OR e.shop.id = :shopId)"
            + " AND (:requestedBy IS NULL OR e.requestedBy = :requestedBy)")
    Page<Expense> search(@Param("business") Business business, @Param("status") String status,
                         @Param("shopId") Long shopId, @Param("requestedBy") String requestedBy, Pageable pageable);

    Optional<Expense> findByIdAndBusiness(Long id, Business business);

    long countByBusinessAndStatus(Business business, String status);

    long countByBusiness(Business business);
}
