package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Constants.StockRequestStatus;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.StockRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRequestRepository extends JpaRepository<StockRequest, Long> {

    Optional<StockRequest> findByIdAndBusiness(Long id, Business business);

    @Query("SELECT r FROM StockRequest r WHERE r.business = :business " +
           "AND (:status IS NULL OR r.status = :status) ORDER BY r.createdAt DESC, r.id DESC")
    List<StockRequest> search(@Param("business") Business business, @Param("status") StockRequestStatus status);

    long countByBusinessAndStatus(Business business, StockRequestStatus status);
}
