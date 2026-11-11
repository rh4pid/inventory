package com.ultimate.inventory.DTO;

import com.ultimate.inventory.Constants.SalaryType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class EmployeeDTO {

    @NotNull(message = "Business user ID is required")
    private Long businessUserId;

    private String position;

    private String department;

    @Positive(message = "Salary must be positive")
    private Double salary;

    private SalaryType salaryType;

    private Date startDate;

    private Date endDate;
}
