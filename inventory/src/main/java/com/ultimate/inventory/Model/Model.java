package com.ultimate.inventory.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@MappedSuperclass
public abstract class Model {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name= "created_at")
    private Date createdAt;
    @Column(name= "updated_at")
    private Date updatedAt;
    @Column(name= "created_by")
    private String createdBy;
    @Column(name= "updated_by")
    private String updatedBy;
}
