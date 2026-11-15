package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByProduct(Product product);
    Optional<ProductImage> findByImageKey(String imageKey);
}
