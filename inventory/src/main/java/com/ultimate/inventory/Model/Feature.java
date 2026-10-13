package com.ultimate.inventory.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "feature")
public class Feature extends Model{

    @Column(unique = true)
    private String Name;
}
