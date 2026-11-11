package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Constants.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class SaleDTO {

    @NotNull(message = "Shop ID is required")
    private Long shopId;

    private Long customerId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Items are required")
    @NotEmpty(message = "Sale must have at least one item")
    @Valid
    private List<SaleItemDTO> items;

    private BigDecimal discountAmount;
    private BigDecimal taxRate;
    private String notes;
}
