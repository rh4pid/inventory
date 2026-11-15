package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Feature;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Repository.FeatureRepository;
import com.ultimate.inventory.Repository.ProductRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.FeatureService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Product features: one row per product + feature + value, e.g. (TSH-RED-M, Color, Red).
 * Features that don't exist yet are created. A value replaces the product's existing value for that feature.
 */
@Component
public class ProductFeatureImporter implements Importer {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("product", "Product", "The product's SKU, or its exact name.", "TSH-RED-M"),
            ImportColumn.required("feature", "Feature", "e.g. Color, Size, Brand. New features are created for you.", "Color"),
            ImportColumn.required("value", "Value", "The product's value for that feature.", "Red"));

    private final BusinessAccessHelper access;
    private final ProductRepository productRepo;
    private final FeatureRepository featureRepo;
    private final FeatureService featureService;

    public ProductFeatureImporter(BusinessAccessHelper access, ProductRepository productRepo,
                                  FeatureRepository featureRepo, FeatureService featureService) {
        this.access = access;
        this.productRepo = productRepo;
        this.featureRepo = featureRepo;
        this.featureService = featureService;
    }

    @Override public ImportKind kind() { return ImportKind.PRODUCT_FEATURES; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public List<String> notes() {
        return List.of("One row per feature: a red, medium T-shirt takes two rows (Color = Red, Size = M).",
                "Upload your products first, so the SKUs or names below exist.");
    }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireCapability(businessId, email, Capability.MANAGE_CATALOG);
    }

    private static String key(String s) { return s.trim().toLowerCase(Locale.ROOT); }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        Map<String, Product> bySku = new HashMap<>();
        Map<String, List<Product>> byName = new HashMap<>();
        productRepo.findByBusinessAndDeletedFalse(business, Pageable.unpaged()).forEach(p -> {
            if (p.getSku() != null && !p.getSku().isBlank()) bySku.put(key(p.getSku()), p);
            byName.computeIfAbsent(key(p.getProductName()), k -> new ArrayList<>()).add(p);
        });
        Set<String> knownFeatures = new HashSet<>();
        featureRepo.findByBusinessAndDeletedFalseOrderByNameAsc(business).forEach(f -> knownFeatures.add(key(f.getName())));

        Set<String> seen = new HashSet<>();
        Set<String> newFeatures = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Plan plan = new Plan();
        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String productRef = c.text("product", 150);
            String featureName = c.text("feature", 80);
            String value = c.text("value", 120);

            Product product = null;
            if (productRef != null) {
                product = bySku.get(key(productRef));
                if (product == null) {
                    List<Product> named = byName.getOrDefault(key(productRef), List.of());
                    if (named.size() == 1) product = named.getFirst();
                    else if (named.size() > 1) c.error("product", named.size() + " products are called " + productRef + ": use the SKU");
                    else c.error("product", "No product with SKU or name " + productRef);
                }
            }
            if (product != null && featureName != null
                    && !seen.add(product.getId() + "|" + key(featureName))) {
                c.error("feature", productRef + " has " + featureName + " more than once in this file");
            }
            if (!c.ok()) continue;
            if (!knownFeatures.contains(key(featureName))) newFeatures.add(featureName.trim());

            Product p = product;
            String fName = featureName.trim();
            String v = value.trim();
            plan.add(row.number(), r -> {
                Feature f = featureService.findOrCreate(businessId, fName, email);
                featureService.setOne(p, f, v, email);
                r.addCreated();
            });
        }
        if (!newFeatures.isEmpty()) result.note("New features will be created: " + String.join(", ", newFeatures));
        return plan;
    }
}
