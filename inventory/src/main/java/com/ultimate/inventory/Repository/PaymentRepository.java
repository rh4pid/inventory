package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByInvoice(Invoice invoice);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.invoice = :invoice")
    BigDecimal sumAmountByInvoice(@Param("invoice") Invoice invoice);
}
