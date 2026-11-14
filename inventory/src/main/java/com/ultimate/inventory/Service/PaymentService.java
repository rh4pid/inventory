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

// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
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
    public Payment recordPayment(Long businessId, Long invoiceId, InvoiceType type, PaymentDTO dto, String userEmail) {
        // Customers pay at the till (sellers); paying suppliers is for managers and admins.
        accessHelper.requireCapability(businessId, userEmail, type == InvoiceType.SALES
                ? com.ultimate.inventory.Constants.Capability.SELL : com.ultimate.inventory.Constants.Capability.PAY_SUPPLIERS);
        Invoice invoice = requireInvoice(businessId, invoiceId, type, userEmail);

        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            throw new AppException(HttpStatus.CONFLICT, "Cannot record payment on a void invoice");
        }
        if (dto.getPaymentMethod() == PaymentMethod.CREDIT) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Choose how the money was paid (cash, bank, mobile money…)");
        }
        // Returns lower what is owed.
        BigDecimal outstanding = InvoiceAdjustmentService.balanceDue(invoice);
        if (outstanding.signum() <= 0) {
            throw new AppException(HttpStatus.CONFLICT, "Nothing is owed on this invoice");
        }
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
        InvoiceAdjustmentService.refreshPaymentStatus(invoice);
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

    public List<Payment> listPayments(Long businessId, Long invoiceId, InvoiceType type, String userEmail) {
        return paymentRepo.findByInvoice(requireInvoice(businessId, invoiceId, type, userEmail));
    }

    /** The invoice, if it is of this type, in this business and at a location the caller works at. */
    private Invoice requireInvoice(Long businessId, Long invoiceId, InvoiceType type, String userEmail) {
        com.ultimate.inventory.Model.BusinessUser member = accessHelper.requireCapability(businessId, userEmail,
                type == InvoiceType.SALES ? com.ultimate.inventory.Constants.Capability.SELL : com.ultimate.inventory.Constants.Capability.RECEIVE);
        Invoice invoice = invoiceRepo.findByIdAndBusiness(invoiceId, accessHelper.requireBusiness(businessId))
                .filter(i -> i.getType() == type)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getShop() != null && !accessHelper.canUseShop(member, invoice.getShop())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return invoice;
    }
}
