package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "supplier")
public class Supplier extends Model {

    @ManyToOne
    @JoinColumn(name = "business")
    private Business business;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "relationship_officer")
    private String relationshipOfficer;

    @Column(name = "company_email")
    private String companyEmail;

    @Column(name = "rm_email")
    private String rmEmail;

    @Column(name = "telephone_primary")
    private String telephonePrimary;

    @Column(name = "telephone_secondary")
    private String telephoneSecondary;
}
