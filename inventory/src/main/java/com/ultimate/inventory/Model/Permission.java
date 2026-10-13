package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "permission")
public class Permission extends Model {
    @ManyToOne
    @JoinColumn(name = "business")
    private Business business;

    @Column(name = "permission_name")
    private String permissionName;
    @Column(name = "description")
    private String description;
}
