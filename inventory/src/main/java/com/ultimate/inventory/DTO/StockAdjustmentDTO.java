package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StockAdjustmentDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    /** Taken from the URL path; ignored in the body. */
    private Long shopId;

    /**
     * The counted quantity in base units: for the whole location, or for one batch when
     * {@link #batchId} is given.
     */
    @NotNull(message = "New quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Double newQuantity;

    /** Count one batch instead of the whole location. */
    private Long batchId;

    /** When a location-level count finds extra stock, the extra goes into a batch with these details. */
    @Size(max = 80)
    private String batchNumber;
    private LocalDate expiryDate;

    private String notes;
    private String reason;
}
