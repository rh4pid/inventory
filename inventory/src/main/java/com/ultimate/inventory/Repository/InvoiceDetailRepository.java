package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.InvoiceDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceDetailRepository extends JpaRepository<InvoiceDetail, Long> {
    List<InvoiceDetail> findByInvoice(Invoice invoice);
}
