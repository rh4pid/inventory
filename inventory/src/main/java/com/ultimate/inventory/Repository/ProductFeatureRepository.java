package com.ultimate.inventory.Repository;

import com.ultimate.inventory.Model.Feature;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.ProductFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductFeatureRepository extends JpaRepository<ProductFeature, Long> {

    List<ProductFeature> findByProduct(Product product);

    @Query("select pf from ProductFeature pf where pf.product.id in :ids and pf.feature.deleted = false order by pf.feature.name")
    List<ProductFeature> findForProducts(@Param("ids") Collection<Long> productIds);

    List<ProductFeature> findByFeature(Feature feature);

    @Modifying
    @Query("delete from ProductFeature pf where pf.feature = :feature")
    void deleteByFeature(@Param("feature") Feature feature);
}
