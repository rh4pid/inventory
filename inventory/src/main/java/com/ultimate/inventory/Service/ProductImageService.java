package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.ProductImage;
import com.ultimate.inventory.Repository.ProductImageRepository;
import com.ultimate.inventory.Repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/** Product pictures: upload (replaces the old one), remove, and serve by their public key. */
@Service
public class ProductImageService {

    static final long MAX_BYTES = 1_500_000;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductImageRepository imageRepo;
    private final ProductRepository productRepo;
    private final BusinessAccessHelper accessHelper;

    public ProductImageService(ProductImageRepository imageRepo, ProductRepository productRepo, BusinessAccessHelper accessHelper) {
        this.imageRepo = imageRepo;
        this.productRepo = productRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Product upload(Long businessId, Long productId, MultipartFile file, String email) {
        Product product = requireProduct(businessId, productId, email);
        if (file == null || file.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Choose a picture");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TYPES.contains(type)) throw new AppException(HttpStatus.BAD_REQUEST, "Use a JPG, PNG or WebP picture");
        if (file.getSize() > MAX_BYTES) throw new AppException(HttpStatus.BAD_REQUEST, "The picture is too big (1.5 MB at most)");

        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw new AppException(HttpStatus.BAD_REQUEST, "Couldn't read the picture"); }

        ProductImage img = imageRepo.findByProduct(product).orElseGet(() -> {
            ProductImage n = new ProductImage();
            n.setProduct(product);
            n.setCreatedBy(email);
            n.setCreatedAt(new Date());
            return n;
        });
        img.setImageKey(newKey()); // a new link each time, so browsers never show the old picture
        img.setContentType(type);
        img.setData(bytes);
        img.setUpdatedBy(email);
        img.setUpdatedAt(new Date());
        imageRepo.save(img);

        product.setImage(img.getImageKey());
        product.setUpdatedBy(email);
        product.setUpdatedAt(new Date());
        return productRepo.save(product);
    }

    @Transactional
    public Product remove(Long businessId, Long productId, String email) {
        Product product = requireProduct(businessId, productId, email);
        imageRepo.findByProduct(product).ifPresent(imageRepo::delete);
        product.setImage(null);
        product.setUpdatedBy(email);
        product.setUpdatedAt(new Date());
        return productRepo.save(product);
    }

    @Transactional(readOnly = true)
    public Optional<ProductImage> byKey(String key) {
        if (key == null || !key.matches("[0-9a-f]{32}")) return Optional.empty();
        return imageRepo.findByImageKey(key).map(i -> { i.getData(); return i; });
    }

    private Product requireProduct(Long businessId, Long productId, String email) {
        accessHelper.requireCapability(businessId, email, Capability.MANAGE_CATALOG);
        Business business = accessHelper.requireBusiness(businessId);
        return productRepo.findByIdAndBusinessAndDeletedFalse(productId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private static String newKey() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
