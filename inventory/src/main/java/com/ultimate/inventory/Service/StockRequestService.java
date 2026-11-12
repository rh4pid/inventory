package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.StockMovementType;
import com.ultimate.inventory.Constants.StockRequestStatus;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.StockRequest;
import com.ultimate.inventory.Repository.StockRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/** Approve, reject or cancel stock changes that are waiting for a manager. */
@Service
public class StockRequestService {

    private static final Logger log = LoggerFactory.getLogger(StockRequestService.class);

    private final StockRequestRepository requestRepo;
    private final InventoryService inventoryService;
    private final BusinessAccessHelper accessHelper;

    public StockRequestService(StockRequestRepository requestRepo, InventoryService inventoryService,
                               BusinessAccessHelper accessHelper) {
        this.requestRepo = requestRepo;
        this.inventoryService = inventoryService;
        this.accessHelper = accessHelper;
    }

    public List<StockRequest> list(Long businessId, StockRequestStatus status, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        return requestRepo.search(accessHelper.requireBusiness(businessId), status);
    }

    public long pendingCount(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        return requestRepo.countByBusinessAndStatus(accessHelper.requireBusiness(businessId), StockRequestStatus.PENDING);
    }

    /** Applies the change. If stock has since run out, nothing changes and the request stays pending. */
    @Transactional
    public StockRequest approve(Long businessId, Long requestId, String note, String userEmail) {
        requireManager(businessId, userEmail);
        StockRequest r = requirePending(businessId, requestId);
        if (r.getRequestedBy().equalsIgnoreCase(userEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You cannot approve your own request");
        }
        String notes = InventoryService.withReason(r.getReason(), r.getNotes());
        InventoryService.Ctx ctx = new InventoryService.Ctx(notes, r.getRequestedBy(), userEmail,
                "REQ-" + r.getId(), InventoryService.Entered.NONE);

        if (r.getRequestType() == StockMovementType.STOCK_OUT) {
            inventoryService.executeStockOut(r.getProduct(), r.getShop(), r.getQuantity(), r.getUnitId(),
                    r.getBatchId(), ctx);
        } else {
            inventoryService.count(r.getProduct(), r.getShop(), r.getBatchId(), r.getCountedQuantity(),
                    r.getExpectedQuantity(), r.getBatchNumber(), r.getExpiryDate(), ctx);
        }
        decide(r, StockRequestStatus.APPROVED, note, userEmail);
        log.info("Stock request {} approved by {}", r.getId(), userEmail);
        return requestRepo.save(r);
    }

    @Transactional
    public StockRequest reject(Long businessId, Long requestId, String note, String userEmail) {
        requireManager(businessId, userEmail);
        StockRequest r = requirePending(businessId, requestId);
        decide(r, StockRequestStatus.REJECTED, note, userEmail);
        return requestRepo.save(r);
    }

    /** The person who asked can withdraw their own pending request. */
    @Transactional
    public StockRequest cancel(Long businessId, Long requestId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        StockRequest r = requirePending(businessId, requestId);
        if (!r.getRequestedBy().equalsIgnoreCase(userEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only the person who made the request can cancel it");
        }
        decide(r, StockRequestStatus.CANCELLED, null, userEmail);
        return requestRepo.save(r);
    }

    private void decide(StockRequest r, StockRequestStatus status, String note, String by) {
        r.setStatus(status);
        r.setDecidedBy(by);
        r.setDecidedAt(new Date());
        r.setDecisionNote(note != null && !note.isBlank() ? note.trim() : null);
        r.setUpdatedBy(by);
        r.setUpdatedAt(new Date());
    }

    private StockRequest requirePending(Long businessId, Long requestId) {
        Business business = accessHelper.requireBusiness(businessId);
        StockRequest r = requestRepo.findByIdAndBusiness(requestId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Request not found"));
        if (r.getStatus() != StockRequestStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "This request is already " + r.getStatus().name().toLowerCase());
        }
        return r;
    }

    private BusinessUser requireManager(Long businessId, String email) {
        return accessHelper.requireRole(businessId, email, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
    }
}
