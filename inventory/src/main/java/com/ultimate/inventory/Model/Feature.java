package com.ultimate.inventory.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Something a product can be described by, e.g. Color or Size. Defined once per company. */
@Getter
@Setter
@Entity
@Table(name = "feature")
public class Feature extends Model {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    private Boolean deleted = false;
}
