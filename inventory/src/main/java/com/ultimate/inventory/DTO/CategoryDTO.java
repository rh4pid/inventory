package com.ultimate.inventory.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryDTO {

    @NotBlank(message = "Category name is required")
    private String categoryName;

    private Long parentId;
}
