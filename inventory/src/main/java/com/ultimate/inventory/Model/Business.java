package com.ultimate.inventory.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "business")
public class Business extends Model{

    @Column(name = "name")
    private String name;
    @Column(unique = true, nullable = false)
    private String code;
    @Column(name = "business_logo")
    private String businessLogo;
    @Column(name = "business_email")
    private String businessEmail;
    @Column(name = "business_contact")
    private String businessContact;
    @Column(name = "business_secondary_contact")
    private String businessSecondaryContact;

    private Boolean locked;
    private Boolean suspended;
    private Boolean deleted;
}
