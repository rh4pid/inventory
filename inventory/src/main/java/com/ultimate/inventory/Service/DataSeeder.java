package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import com.ultimate.inventory.utils.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepo;
    private final BusinessRepository businessRepo;
    private final BusinessUserRepo businessUserRepo;
    private final ShopRepository shopRepo;
    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;
    private final InventoryRepository inventoryRepo;
    private final SupplierRepository supplierRepo;
    private final CustomerRepository customerRepo;
    private final EmployeeRepository employeeRepo;
    private final AccountService accountService;
    private final Security security;

    public DataSeeder(
            UserRepository userRepo, BusinessRepository businessRepo,
            BusinessUserRepo businessUserRepo, ShopRepository shopRepo,
            CategoryRepository categoryRepo, ProductRepository productRepo,
            InventoryRepository inventoryRepo, SupplierRepository supplierRepo,
            CustomerRepository customerRepo, EmployeeRepository employeeRepo,
            AccountService accountService, Security security) {
        this.userRepo = userRepo;
        this.businessRepo = businessRepo;
        this.businessUserRepo = businessUserRepo;
        this.shopRepo = shopRepo;
        this.categoryRepo = categoryRepo;
        this.productRepo = productRepo;
        this.inventoryRepo = inventoryRepo;
        this.supplierRepo = supplierRepo;
        this.customerRepo = customerRepo;
        this.employeeRepo = employeeRepo;
        this.accountService = accountService;
        this.security = security;
    }

    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) {
            log.info("Database already seeded — skipping.");
            return;
        }

        log.info("Seeding demo data…");

        // ── User 1 (Owner / Manager across companies) ─────────────────────────
        User owner = new User();
        owner.setFirstName("Demo");
        owner.setLastName("Owner");
        owner.setEmail("admin@demo.com");
        owner.setPassword(security.hashPassword("password123"));
        owner.setCreatedAt(new Date());
        owner.setCreatedBy("seeder");
        owner = userRepo.save(owner);

        // ── User 2 (Cashier at Retail Corner) ────────────────────────────────
        User cashier = new User();
        cashier.setFirstName("Jane");
        cashier.setLastName("Cashier");
        cashier.setEmail("cashier@demo.com");
        cashier.setPassword(security.hashPassword("password123"));
        cashier.setCreatedAt(new Date());
        cashier.setCreatedBy("seeder");
        cashier = userRepo.save(cashier);

        // ── User 3 (Manager at Demo Business) ────────────────────────────────
        User manager = new User();
        manager.setFirstName("Sam");
        manager.setLastName("Manager");
        manager.setEmail("manager@demo.com");
        manager.setPassword(security.hashPassword("password123"));
        manager.setCreatedAt(new Date());
        manager.setCreatedBy("seeder");
        manager = userRepo.save(manager);

        // ══ COMPANY 1: Demo Business (owner = admin@demo.com) ═════════════════
        Business biz1 = makeBusiness("Demo Business", "contact@demobusiness.com", "+1 555 000 1234");
        addMember(biz1, owner,   UserRoles.OWNER);
        addMember(biz1, manager, UserRoles.MANAGER);
        addMember(biz1, cashier, UserRoles.CASHIER);

        Shop shop1 = makeShop("Main Store", "123 High Street", biz1);
        Shop shop1b = makeShop("Warehouse", "45 Industrial Ave", biz1);
        shop1b.setLocationType(com.ultimate.inventory.Constants.LocationType.WAREHOUSE);
        shop1b = shopRepo.save(shop1b);

        Category elec   = makeCategory("Electronics", null, biz1);
        Category food   = makeCategory("Food & Beverages", null, biz1);
        Category cloth  = makeCategory("Clothing", null, biz1);
        Category phones = makeCategory("Smartphones", elec, biz1);
        Category laptops= makeCategory("Laptops", elec, biz1);

        Product iphone  = makeProduct("iPhone 15 Pro 128GB", "IP15P-128", biz1, phones,  850.00, 1050.00, 5);
        Product galaxy  = makeProduct("Samsung Galaxy S24",  "SGS24-256", biz1, phones,  720.00,  950.00, 5);
        Product macbook = makeProduct("MacBook Air M3 13\"", "MBA-M3-13", biz1, laptops,1050.00, 1299.00, 3);
        Product dell    = makeProduct("Dell XPS 15",         "DXPS15-512",biz1, laptops,1100.00, 1399.00, 3);
        Product water   = makeProduct("Mineral Water 500ml", "MW-500",    biz1, food,     0.30,    0.75, 20);
        Product juice   = makeProduct("Orange Juice 1L",     "OJ-1L",     biz1, food,     1.20,    2.50, 20);
        Product tshirt  = makeProduct("Cotton T-Shirt",      "CTS-BLK",   biz1, cloth,    8.00,   15.00, 10);
        Product jeans   = makeProduct("Slim Fit Jeans",      "SFJ-BLU",   biz1, cloth,   18.00,   35.00, 10);

        seedInventory(iphone,  shop1,   25.0);
        seedInventory(galaxy,  shop1,   18.0);
        seedInventory(macbook, shop1,    8.0);
        seedInventory(dell,    shop1,    6.0);
        seedInventory(water,   shop1,  200.0);
        seedInventory(juice,   shop1,  150.0);
        seedInventory(tshirt,  shop1,   50.0);
        seedInventory(jeans,   shop1,   40.0);
        seedInventory(macbook, shop1b,  12.0);
        seedInventory(dell,    shop1b,  10.0);

        makeSupplier("TechWorld Distributors", "Alex Mensah",  "alex@techworld.com",    "+1 555 100 2001", biz1);
        makeSupplier("Grocery Plus Wholesale", "Ama Owusu",    "ama@groceryplus.com",   "+1 555 200 3002", biz1);
        makeSupplier("Fashion Hub Ltd",        "Kweku Asante", "kweku@fashionhub.com",  "+1 555 300 4003", biz1);

        makeCustomer("John",     "Boateng",   "john@example.com",    "+1 555 400 5001", biz1);
        makeCustomer("Abena",    "Mensah",    "abena@example.com",   "+1 555 400 5002", biz1);
        makeCustomer("Samuel",   "Osei",      "samuel@example.com",  "+1 555 400 5003", biz1);
        makeCustomer("Grace",    "Amponsah",  "grace@example.com",   "+1 555 400 5004", biz1);
        makeCustomer("Emmanuel", "Darko",     null,                  "+1 555 400 5005", biz1);

        // Employees for biz1
        BusinessUser ownerBu   = businessUserRepo.findByBusinessAndUser(biz1, owner);
        BusinessUser managerBu = businessUserRepo.findByBusinessAndUser(biz1, manager);
        BusinessUser cashierBu = businessUserRepo.findByBusinessAndUser(biz1, cashier);

        makeEmployee(ownerBu,   "Chief Executive Officer", "Executive", 8000.0, SalaryType.MONTHLY);
        makeEmployee(managerBu, "Store Manager",           "Operations",5000.0, SalaryType.MONTHLY);
        makeEmployee(cashierBu, "Cashier",                 "Sales",     2500.0, SalaryType.MONTHLY);

        try {
            accountService.setupDefaultAccounts(biz1.getId(), owner.getEmail());
        } catch (Exception e) {
            log.warn("Accounts setup skipped for biz1: {}", e.getMessage());
        }

        // ══ COMPANY 2: Retail Corner (owner = admin@demo.com, MANAGER role for cashier) ═
        Business biz2 = makeBusiness("Retail Corner Ltd", "hello@retailcorner.com", "+1 555 999 8888");

        // owner is ADMIN here (not OWNER — different role)
        addMember(biz2, owner,   UserRoles.ADMIN);
        // cashier is CASHIER here too
        addMember(biz2, cashier, UserRoles.CASHIER);

        Shop shop2 = makeShop("Downtown Branch", "77 Market Street", biz2);

        Category pharma = makeCategory("Pharmaceuticals", null, biz2);
        Category beauty = makeCategory("Beauty & Personal Care", null, biz2);
        Category grocery= makeCategory("Grocery", null, biz2);

        Product paracetamol = makeProduct("Paracetamol 500mg (x20)", "PAR-500", biz2, pharma, 0.80, 2.00, 50);
        Product vitaminC    = makeProduct("Vitamin C 1000mg (x30)",  "VIT-C30", biz2, pharma, 2.50, 6.00, 30);
        Product shampoo     = makeProduct("Herbal Shampoo 400ml",    "SH-400",  biz2, beauty, 3.00, 7.50, 20);
        Product lotion      = makeProduct("Moisturising Lotion 250ml","ML-250", biz2, beauty, 2.20, 5.00, 20);
        Product rice        = makeProduct("Basmati Rice 5kg",         "RIC-5KG",biz2, grocery,4.00, 9.00, 15);
        Product sugar       = makeProduct("White Sugar 1kg",          "SUG-1KG",biz2, grocery,0.60, 1.50, 30);

        seedInventory(paracetamol, shop2, 300.0);
        seedInventory(vitaminC,    shop2, 150.0);
        seedInventory(shampoo,     shop2,  80.0);
        seedInventory(lotion,      shop2,  60.0);
        seedInventory(rice,        shop2, 100.0);
        seedInventory(sugar,       shop2, 200.0);

        makeSupplier("PharmaCo Wholesale",  "Dr. Yaw Asare",  "yaw@pharmacowhl.com",  "+1 555 700 1111", biz2);
        makeSupplier("Beauty Direct Ltd",   "Esi Turkson",    "esi@beautydirect.com", "+1 555 700 2222", biz2);

        makeCustomer("Kofi",     "Amponsah", "kofi@example.com",  "+1 555 800 1001", biz2);
        makeCustomer("Adjoa",    "Wiredu",   "adjoa@example.com", "+1 555 800 1002", biz2);
        makeCustomer("Patrick",  "Boakye",   null,                "+1 555 800 1003", biz2);

        BusinessUser ownerBu2  = businessUserRepo.findByBusinessAndUser(biz2, owner);
        BusinessUser cashierBu2= businessUserRepo.findByBusinessAndUser(biz2, cashier);
        makeEmployee(ownerBu2,   "Operations Director", "Management", 6000.0, SalaryType.MONTHLY);
        makeEmployee(cashierBu2, "Sales Cashier",       "Sales",      2200.0, SalaryType.MONTHLY);

        try {
            accountService.setupDefaultAccounts(biz2.getId(), owner.getEmail());
        } catch (Exception e) {
            log.warn("Accounts setup skipped for biz2: {}", e.getMessage());
        }

        log.info("═══════════════════════════════════════════════════════");
        log.info("  Seed complete. Credentials:");
        log.info("  admin@demo.com   / password123  (OWNER biz1, ADMIN biz2)");
        log.info("  manager@demo.com / password123  (MANAGER biz1 only)");
        log.info("  cashier@demo.com / password123  (CASHIER biz1 + biz2)");
        log.info("═══════════════════════════════════════════════════════");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Business makeBusiness(String name, String email, String contact) {
        Business b = new Business();
        b.setName(name);
        b.setBusinessEmail(email);
        b.setBusinessContact(contact);
        b.setLocked(false);
        b.setSuspended(false);
        b.setDeleted(false);
        b.setCreatedAt(new Date());
        b.setCreatedBy("seeder");
        b.setCode("INIT-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        b = businessRepo.save(b);
        b.setCode("BIZ" + String.format("%05d", b.getId()));
        return businessRepo.save(b);
    }

    private BusinessUser addMember(Business biz, User user, UserRoles role) {
        BusinessUser bu = new BusinessUser();
        bu.setBusiness(biz);
        bu.setUser(user);
        bu.setRoles(role);
        bu.setCreatedAt(new Date());
        bu.setCreatedBy("seeder");
        return businessUserRepo.save(bu);
    }

    private Shop makeShop(String name, String location, Business biz) {
        Shop s = new Shop();
        s.setBusiness(biz);
        s.setShopName(name);
        s.setShopLocation(location);
        s.setActive(true);
        s.setCreatedAt(new Date());
        s.setCreatedBy("seeder");
        s.setShopCode("INIT-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        s = shopRepo.save(s);
        s.setShopCode(biz.getCode() + "-SHOP-" + String.format("%04d", s.getId()));
        return shopRepo.save(s);
    }

    private Category makeCategory(String name, Category parent, Business biz) {
        Category c = new Category();
        c.setBusiness(biz);
        c.setCategoryName(name);
        c.setParent(parent);
        c.setDeleted(false);
        c.setCreatedAt(new Date());
        c.setCreatedBy("seeder");
        return categoryRepo.save(c);
    }

    private Product makeProduct(String name, String sku, Business biz, Category cat,
                                double cost, double price, int reorder) {
        Product p = new Product();
        p.setBusiness(biz);
        p.setProductName(name);
        p.setSku(sku);
        p.setCategory(cat);
        p.setCostPrice(cost);
        p.setSellingPrice(price);
        p.setActualPrice(price);
        p.setReorderLevel(reorder);
        p.setTotalCount(0.0);
        p.setDeleted(false);
        p.setCreatedAt(new Date());
        p.setCreatedBy("seeder");
        return productRepo.save(p);
    }

    private void seedInventory(Product product, Shop shop, double qty) {
        Inventory inv = new Inventory();
        inv.setProduct(product);
        inv.setShop(shop);
        inv.setQuantity(qty);
        inv.setCreatedAt(new Date());
        inv.setCreatedBy("seeder");
        inventoryRepo.save(inv);
        product.setTotalCount(product.getTotalCount() + qty);
        productRepo.save(product);
    }

    private void makeSupplier(String company, String officer, String email, String phone, Business biz) {
        Supplier s = new Supplier();
        s.setBusiness(biz);
        s.setCompanyName(company);
        s.setRelationshipOfficer(officer);
        s.setCompanyEmail(email);
        s.setTelephonePrimary(phone);
        s.setDeleted(false);
        s.setCreatedAt(new Date());
        s.setCreatedBy("seeder");
        supplierRepo.save(s);
    }

    private void makeCustomer(String first, String last, String email, String phone, Business biz) {
        Customer c = new Customer();
        c.setBusiness(biz);
        c.setFirstName(first);
        c.setLastName(last);
        c.setEmail(email);
        c.setPhone(phone);
        c.setLoyaltyPoints(0.0);
        c.setTotalPurchases(0.0);
        c.setDeleted(false);
        c.setCreatedAt(new Date());
        c.setCreatedBy("seeder");
        customerRepo.save(c);
    }

    private void makeEmployee(BusinessUser bu, String position, String dept, double salary, SalaryType type) {
        Employee e = new Employee();
        e.setBusinessUser(bu);
        e.setPosition(position);
        e.setDepartment(dept);
        e.setSalary(salary);
        e.setSalaryType(type);
        e.setStartDate(new Date());
        e.setCreatedAt(new Date());
        e.setCreatedBy("seeder");
        employeeRepo.save(e);
    }
}
