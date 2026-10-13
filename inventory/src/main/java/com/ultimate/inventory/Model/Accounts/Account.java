package com.ultimate.inventory.Model.Accounts;

import com.ultimate.inventory.Constants.AccountPurpose;
import com.ultimate.inventory.Constants.AccountType;
import com.ultimate.inventory.Model.Model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "account")
public class Account extends Model {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private Boolean active = true;
}
