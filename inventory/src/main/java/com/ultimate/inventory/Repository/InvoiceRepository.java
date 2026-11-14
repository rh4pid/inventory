package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Constants.InvoiceType;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Page<Invoice> findByBusinessAndType(Business business, InvoiceType type, Pageable pageable);
    Page<Invoice> findByShopAndType(Shop shop, InvoiceType type, Pageable pageable);
    Page<Invoice> findByBusinessAndTypeAndShop_IdIn(Business business, InvoiceType type,
                                                    java.util.Collection<Long> shopIds, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("SELECT i FROM Invoice i WHERE i.business = :business AND i.type = :type " +
           "AND (i.createdAt >= :since OR (i.paymentStatus IN (com.ultimate.inventory.Constants.PaymentStatus.UNPAID, " +
           "com.ultimate.inventory.Constants.PaymentStatus.PARTIAL) AND i.invoiceStatus <> com.ultimate.inventory.Constants.InvoiceStatus.VOID))")
    java.util.List<Invoice> findForDashboard(@org.springframework.data.repository.query.Param("business") Business business,
                                     @org.springframework.data.repository.query.Param("type") InvoiceType type,
                                     @org.springframework.data.repository.query.Param("since") java.util.Date since);
    Optional<Invoice> findByIdAndBusiness(Long id, Business business);

    java.util.List<Invoice> findByBusinessOrderByIdAsc(Business business);
}
