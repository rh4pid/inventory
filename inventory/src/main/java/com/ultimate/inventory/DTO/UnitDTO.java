package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnitDTO {
    @NotBlank(message = "Unit name is required")
    @Size(max = 50)
    private String name;
    @Size(max = 15)
    private String symbol;
}
