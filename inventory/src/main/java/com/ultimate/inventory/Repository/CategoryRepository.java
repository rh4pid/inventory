package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByBusinessAndDeletedFalse(Business business);
    List<Category> findByBusinessAndParentIsNullAndDeletedFalse(Business business);
    Optional<Category> findByIdAndBusinessAndDeletedFalse(Long id, Business business);
}
