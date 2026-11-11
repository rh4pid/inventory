package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Constants.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PurchaseDTO {

    @NotNull(message = "Shop ID is required")
    private Long shopId;

    private Long supplierId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Items are required")
    @NotEmpty(message = "Purchase must have at least one item")
    @Valid
    private List<PurchaseItemDTO> items;

    private String notes;
}
