package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.InvoiceDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceDetailRepository extends JpaRepository<InvoiceDetail, Long> {
    List<InvoiceDetail> findByInvoice(Invoice invoice);

    /** Lines of several invoices in one query (with their product), e.g. for a list of recent orders. */
    @org.springframework.data.jpa.repository.Query(
            "select d from InvoiceDetail d join fetch d.product where d.invoice in :invoices order by d.id")
    List<InvoiceDetail> findByInvoiceIn(@org.springframework.data.repository.query.Param("invoices") java.util.Collection<Invoice> invoices);
}
