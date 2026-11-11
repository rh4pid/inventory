package com.ultimate.inventory.Model;

import com.ultimate.inventory.Constants.SalaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "employee")
public class Employee extends Model {

    @ManyToOne
    @JoinColumn(name = "business_user_id", nullable = false)
    private BusinessUser businessUser;

    @Column
    private String position;

    @Column
    private String department;

    @Column
    private Double salary;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type")
    private SalaryType salaryType;

    @Column(name = "start_date")
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;
}
