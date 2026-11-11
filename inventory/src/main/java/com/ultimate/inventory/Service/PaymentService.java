package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.InvoiceStatus;
import com.ultimate.inventory.Constants.InvoiceType;
import com.ultimate.inventory.Constants.PaymentStatus;
import com.ultimate.inventory.Constants.PaymentMethod;
import com.ultimate.inventory.DTO.PaymentDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Invoice;
import com.ultimate.inventory.Model.Payment;
import com.ultimate.inventory.Repository.InvoiceRepository;
import com.ultimate.inventory.Repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepo;
    private final InvoiceRepository invoiceRepo;
    private final JournalService journalService;
    private final BusinessAccessHelper accessHelper;

    public PaymentService(PaymentRepository paymentRepo, InvoiceRepository invoiceRepo,
                          JournalService journalService, BusinessAccessHelper accessHelper) {
        this.paymentRepo = paymentRepo;
        this.invoiceRepo = invoiceRepo;
        this.journalService = journalService;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Payment recordPayment(Long businessId, Long invoiceId, PaymentDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);

        Invoice invoice = invoiceRepo.findByIdAndBusiness(invoiceId, accessHelper.requireBusiness(businessId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invoice not found"));

        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            throw new AppException(HttpStatus.CONFLICT, "Cannot record payment on a void invoice");
        }
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            throw new AppException(HttpStatus.CONFLICT, "Invoice is already fully paid");
        }

        BigDecimal outstanding = invoice.getTotalAmount().subtract(invoice.getAmountPaid());
        if (dto.getAmount().compareTo(outstanding) > 0) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Payment amount " + dto.getAmount() + " exceeds outstanding balance " + outstanding);
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(dto.getAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setPaymentReference(dto.getPaymentReference());
        payment.setNotes(dto.getNotes());
        payment.setPaidAt(new Date());
        payment.setPerformedBy(userEmail);
        payment.setCreatedBy(userEmail);
        payment.setCreatedAt(new Date());
        Payment saved = paymentRepo.save(payment);

        BigDecimal newAmountPaid = invoice.getAmountPaid().add(dto.getAmount());
        invoice.setAmountPaid(newAmountPaid);
        invoice.setPaymentStatus(newAmountPaid.compareTo(invoice.getTotalAmount()) >= 0
                ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
        invoice.setUpdatedBy(userEmail);
        invoice.setUpdatedAt(new Date());
        invoiceRepo.save(invoice);

        try {
            journalService.postPaymentJournal(saved, userEmail);
        } catch (Exception e) {
            log.warn("Journal posting skipped for payment on invoice #{}: {}",
                    invoice.getInvoiceNumber(), e.getMessage());
        }

        log.info("Payment recorded: invoice={} amount={} by {}", invoice.getInvoiceNumber(), dto.getAmount(), userEmail);
        return saved;
    }

    public List<Payment> listPayments(Long businessId, Long invoiceId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Invoice invoice = invoiceRepo.findByIdAndBusiness(invoiceId, accessHelper.requireBusiness(businessId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return paymentRepo.findByInvoice(invoice);
    }
}
