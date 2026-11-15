package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Feature;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.ProductFeature;
import com.ultimate.inventory.Repository.FeatureRepository;
import com.ultimate.inventory.Repository.ProductFeatureRepository;
import com.ultimate.inventory.Repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Features (Color, Size, …) and the values products have for them. A product has at most one value
 * per feature. Everyone in the company can read them; changing them needs MANAGE_CATALOG.
 */
@Service
public class FeatureService {

    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);
    static final int MAX_NAME = 80;
    static final int MAX_VALUE = 120;

    private final FeatureRepository featureRepo;
    private final ProductFeatureRepository productFeatureRepo;
    private final ProductRepository productRepo;
    private final BusinessAccessHelper accessHelper;

    public FeatureService(FeatureRepository featureRepo, ProductFeatureRepository productFeatureRepo,
                          ProductRepository productRepo, BusinessAccessHelper accessHelper) {
        this.featureRepo = featureRepo;
        this.productFeatureRepo = productFeatureRepo;
        this.productRepo = productRepo;
        this.accessHelper = accessHelper;
    }

    /** A feature with how many products use it and the values in use (for suggestions). */
    public record FeatureView(Long id, String name, long productCount, List<String> values) {}

    /** One of a product's features. */
    public record Value(Long featureId, String featureName, String value) {}

    /** What the product form sends: feature + value (blank value = remove). */
    public record SetValue(Long featureId, String value) {}

    // ── Features ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FeatureView> list(Long businessId, String email) {
        accessHelper.requireMembership(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        List<FeatureView> out = new ArrayList<>();
        for (Feature f : featureRepo.findByBusinessAndDeletedFalseOrderByNameAsc(business)) {
            List<ProductFeature> uses = productFeatureRepo.findByFeature(f).stream()
                    .filter(pf -> !Boolean.TRUE.equals(pf.getProduct().getDeleted())).toList();
            TreeSet<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            uses.forEach(pf -> values.add(pf.getValue()));
            out.add(new FeatureView(f.getId(), f.getName(), uses.size(), new ArrayList<>(values)));
        }
        return out;
    }

    @Transactional
    public Feature create(Long businessId, String name, String email) {
        requireCatalog(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        String n = cleanName(name);
        requireUniqueName(business, n, null);
        Feature f = new Feature();
        f.setBusiness(business);
        f.setName(n);
        f.setDeleted(false);
        f.setCreatedBy(email);
        f.setCreatedAt(new Date());
        Feature saved = featureRepo.save(f);
        log.info("Feature '{}' created in business {} by {}", n, businessId, email);
        return saved;
    }

    @Transactional
    public Feature rename(Long businessId, Long featureId, String name, String email) {
        requireCatalog(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        Feature f = requireFeature(business, featureId);
        String n = cleanName(name);
        requireUniqueName(business, n, f.getId());
        f.setName(n);
        f.setUpdatedBy(email);
        f.setUpdatedAt(new Date());
        return featureRepo.save(f);
    }

    /**
     * Removes the feature and every product's value for it. Deleted for real (nothing else points at
     * a feature), so the name is free to use again.
     */
    @Transactional
    public void delete(Long businessId, Long featureId, String email) {
        requireCatalog(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        Feature f = requireFeature(business, featureId);
        productFeatureRepo.deleteByFeature(f);
        featureRepo.delete(f);
        log.info("Feature {} deleted in business {} by {}", featureId, businessId, email);
    }

    /** The feature with this name, created if it doesn't exist yet (used by the spreadsheet upload). */
    @Transactional
    public Feature findOrCreate(Long businessId, String name, String email) {
        Business business = accessHelper.requireBusiness(businessId);
        return featureRepo.findFirstByBusinessAndNameIgnoreCaseAndDeletedFalse(business, name.trim())
                .orElseGet(() -> create(businessId, name, email));
    }

    // ── A product's features ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Value> forProduct(Long businessId, Long productId, String email) {
        accessHelper.requireMembership(businessId, email);
        Product product = requireProduct(businessId, productId);
        return productFeatureRepo.findByProduct(product).stream()
                .filter(pf -> !Boolean.TRUE.equals(pf.getFeature().getDeleted()))
                .sorted(Comparator.comparing(pf -> pf.getFeature().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(FeatureService::view).toList();
    }

    /** Features of several products at once (product id -> its features), for lists and grids. */
    @Transactional(readOnly = true)
    public Map<Long, List<Value>> forProducts(Long businessId, Collection<Long> productIds, String email) {
        accessHelper.requireMembership(businessId, email);
        Map<Long, List<Value>> out = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) return out;
        Long bizId = accessHelper.requireBusiness(businessId).getId();
        for (ProductFeature pf : productFeatureRepo.findForProducts(productIds)) {
            if (!pf.getProduct().getBusiness().getId().equals(bizId)) continue; // another company's product id
            out.computeIfAbsent(pf.getProduct().getId(), k -> new ArrayList<>()).add(view(pf));
        }
        return out;
    }

    /**
     * Replaces the product's features with these. Features left out, or sent with a blank value,
     * are removed from the product.
     */
    @Transactional
    public List<Value> setForProduct(Long businessId, Long productId, List<SetValue> values, String email) {
        requireCatalog(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        Product product = requireProduct(businessId, productId);

        Map<Long, String> wanted = new LinkedHashMap<>();
        for (SetValue v : values == null ? List.<SetValue>of() : values) {
            if (v.featureId() == null) throw new AppException(HttpStatus.BAD_REQUEST, "Choose a feature for each value");
            String value = v.value() == null ? "" : v.value().trim();
            if (value.isEmpty()) continue;
            if (value.length() > MAX_VALUE) throw new AppException(HttpStatus.BAD_REQUEST, "Values can be " + MAX_VALUE + " characters at most");
            if (wanted.put(v.featureId(), value) != null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Each feature can only be given once");
            }
        }
        Map<Long, Feature> features = new HashMap<>();
        for (Long id : wanted.keySet()) features.put(id, requireFeature(business, id));

        Map<Long, ProductFeature> current = new HashMap<>();
        for (ProductFeature pf : productFeatureRepo.findByProduct(product)) {
            if (wanted.containsKey(pf.getFeature().getId())) current.put(pf.getFeature().getId(), pf);
            else productFeatureRepo.delete(pf);
        }
        for (Map.Entry<Long, String> e : wanted.entrySet()) {
            ProductFeature pf = current.get(e.getKey());
            if (pf == null) {
                pf = new ProductFeature();
                pf.setProduct(product);
                pf.setFeature(features.get(e.getKey()));
                pf.setCreatedBy(email);
                pf.setCreatedAt(new Date());
            } else if (pf.getValue().equals(e.getValue())) {
                continue;
            } else {
                pf.setUpdatedBy(email);
                pf.setUpdatedAt(new Date());
            }
            pf.setValue(e.getValue());
            productFeatureRepo.save(pf);
        }
        productFeatureRepo.flush();
        return forProduct(businessId, productId, email);
    }

    /** Sets one value (upload): adds it or replaces the product's existing value for that feature. */
    @Transactional
    public void setOne(Product product, Feature feature, String value, String email) {
        ProductFeature pf = productFeatureRepo.findByProduct(product).stream()
                .filter(x -> x.getFeature().getId().equals(feature.getId())).findFirst().orElse(null);
        if (pf == null) {
            pf = new ProductFeature();
            pf.setProduct(product);
            pf.setFeature(feature);
            pf.setCreatedBy(email);
            pf.setCreatedAt(new Date());
        } else {
            pf.setUpdatedBy(email);
            pf.setUpdatedAt(new Date());
        }
        pf.setValue(value.trim());
        productFeatureRepo.save(pf);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Value view(ProductFeature pf) {
        return new Value(pf.getFeature().getId(), pf.getFeature().getName(), pf.getValue());
    }

    private void requireCatalog(Long businessId, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_CATALOG);
    }

    private Feature requireFeature(Business business, Long id) {
        return featureRepo.findByIdAndBusinessAndDeletedFalse(id, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Feature not found"));
    }

    private Product requireProduct(Long businessId, Long productId) {
        Business business = accessHelper.requireBusiness(businessId);
        return productRepo.findByIdAndBusinessAndDeletedFalse(productId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Give the feature a name, e.g. Color or Size");
        if (n.length() > MAX_NAME) throw new AppException(HttpStatus.BAD_REQUEST, "Feature names can be " + MAX_NAME + " characters at most");
        return n;
    }

    private void requireUniqueName(Business business, String name, Long exceptId) {
        featureRepo.findFirstByBusinessAndNameIgnoreCaseAndDeletedFalse(business, name)
                .filter(f -> !f.getId().equals(exceptId))
                .ifPresent(f -> { throw new AppException(HttpStatus.CONFLICT, "You already have a feature called " + f.getName()); });
    }
}
