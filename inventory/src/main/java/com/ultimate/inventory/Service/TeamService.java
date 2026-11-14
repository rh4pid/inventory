package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.ShopUsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * A company's people: the company admin (or a manager, for front-line staff) creates sign-ins,
 * sets each person's role and the locations they work at, resets passwords and removes people.
 *
 * Who can manage whom: owners and admins manage everyone except owners; managers manage
 * cashiers, staff and drivers. Nobody changes their own role or removes themselves.
 */
// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);
    private static final Set<UserRoles> TOP = EnumSet.of(UserRoles.OWNER, UserRoles.ADMIN);
    private static final Set<UserRoles> FRONT_LINE = EnumSet.of(UserRoles.CASHIER, UserRoles.STAFF, UserRoles.DRIVER, UserRoles.SHOPKEEP);
    /** Roles that can be given out here (OWNER is not handed out; SHOPKEEP is legacy). */
    private static final List<UserRoles> ASSIGNABLE = List.of(UserRoles.ADMIN, UserRoles.MANAGER, UserRoles.CASHIER, UserRoles.STAFF, UserRoles.DRIVER);

    private final BusinessAccessHelper accessHelper;
    private final BusinessUserRepo businessUserRepo;
    private final ShopUsersRepository shopUsersRepo;
    private final UserAccounts accounts;

    public TeamService(BusinessAccessHelper accessHelper, BusinessUserRepo businessUserRepo,
                       ShopUsersRepository shopUsersRepo, UserAccounts accounts) {
        this.accessHelper = accessHelper;
        this.businessUserRepo = businessUserRepo;
        this.shopUsersRepo = shopUsersRepo;
        this.accounts = accounts;
    }

    public record Location(Long id, String name) {}

    public record Member(Long id, Long userId, String name, String email, String phone, UserRoles role,
                         List<Location> locations, boolean pendingFirstSignIn, boolean you, boolean canManage) {}

    public record Team(List<Member> members, List<UserRoles> rolesYouCanAssign) {}

    public record NewMember(String firstName, String lastName, String email, String phone, UserRoles role, List<Long> shopIds) {}

    public record Change(UserRoles role, List<Long> shopIds) {}

    /** temporaryPassword is null when the person already had a sign-in (e.g. from another company). */
    public record Added(Member member, String temporaryPassword, boolean existingAccount) {}

    // ── Queries ──────────────────────────────────────────────────────────────

    public Team list(Long businessId, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        List<BusinessUser> active = businessUserRepo.findAllByBusiness(business).stream()
                .filter(BusinessUser::isActiveMember).toList();
        // Everyone's locations in one query rather than one per person.
        java.util.Map<Long, List<ShopUsers>> assignments = active.isEmpty() ? java.util.Map.of()
                : shopUsersRepo.findActiveByBusinessUsers(active).stream()
                        .collect(java.util.stream.Collectors.groupingBy(su -> su.getBusinessUser().getId()));
        List<Member> members = active.stream()
                .sorted(Comparator.comparingInt((BusinessUser m) -> ASSIGNABLE.indexOf(m.getRoles()) < 0 ? -1 : ASSIGNABLE.indexOf(m.getRoles()))
                        .thenComparing(m -> UserAccounts.displayName(m.getUser())))
                .map(m -> view(m, actor, assignments.getOrDefault(m.getId(), List.of()))).toList();
        return new Team(members, assignableBy(actor));
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public Added add(Long businessId, NewMember req, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        UserRoles role = req.role() != null ? req.role() : UserRoles.CASHIER;
        requireCanAssign(actor, role);
        List<Shop> shops = requireShops(req.shopIds(), businessId);

        UserAccounts.Provisioned p = accounts.findOrCreate(req.firstName(), req.lastName(), req.email(), req.phone(), email);
        BusinessUser bu = businessUserRepo.findByBusinessAndUser(business, p.user());
        if (bu != null && bu.isActiveMember()) {
            throw new AppException(HttpStatus.CONFLICT, UserAccounts.displayName(p.user()) + " is already on the team");
        }
        if (bu == null) {
            bu = new BusinessUser();
            bu.setBusiness(business);
            bu.setUser(p.user());
            bu.setCreatedBy(email);
            bu.setCreatedAt(new Date());
        } else {
            bu.setUpdatedBy(email);
            bu.setUpdatedAt(new Date());
        }
        bu.setRoles(role);
        bu.setActive(true);
        bu = businessUserRepo.save(bu);
        syncLocations(bu, shops, email);

        log.info("{} added {} as {} to business {}", email, p.user().getEmail(), role, business.getCode());
        return new Added(view(bu, actor), p.temporaryPassword(), p.existing());
    }

    /** Changes a person's role and/or the locations they work at. */
    @Transactional
    public Member update(Long businessId, Long memberId, Change req, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        BusinessUser bu = requireManageable(actor, memberId, businessId);
        if (req.role() != null && req.role() != bu.getRoles()) {
            requireCanAssign(actor, req.role());
            if (TOP.contains(bu.getRoles()) && !TOP.contains(req.role())) requireAnotherAdmin(bu);
            bu.setRoles(req.role());
        }
        if (req.shopIds() != null) syncLocations(bu, requireShops(req.shopIds(), businessId), email);
        bu.setUpdatedBy(email);
        bu.setUpdatedAt(new Date());
        return view(businessUserRepo.save(bu), actor);
    }

    /**
     * New temporary password. Only for people whose sign-in is used by this company alone:
     * resetting a shared sign-in would lock them out of their other company too.
     */
    @Transactional
    public Added resetPassword(Long businessId, Long memberId, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        BusinessUser bu = requireManageable(actor, memberId, businessId);
        boolean shared = businessUserRepo.findByUser(bu.getUser()).stream()
                .anyMatch(m -> m.isActiveMember() && !m.getBusiness().getId().equals(businessId));
        if (shared) {
            throw new AppException(HttpStatus.CONFLICT, UserAccounts.displayName(bu.getUser()) +
                    " also works for another company with the same sign-in, so it can't be reset here. " +
                    "They can change it under Profile, or ask the system administrator.");
        }
        String temp = accounts.resetToTemporary(bu.getUser(), email);
        log.info("{} reset the password of {} in business {}", email, bu.getUser().getEmail(), businessId);
        return new Added(view(bu, actor), temp, false);
    }

    /** Removes the person from the company. Their history (sales, movements) is kept. */
    @Transactional
    public void remove(Long businessId, Long memberId, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        BusinessUser bu = requireManageable(actor, memberId, businessId);
        if (TOP.contains(bu.getRoles())) requireAnotherAdmin(bu);
        shopUsersRepo.deleteAll(shopUsersRepo.findActiveByBusinessUser(bu));
        bu.setActive(false);
        bu.setUpdatedBy(email);
        bu.setUpdatedAt(new Date());
        businessUserRepo.save(bu);
        log.info("{} removed {} from business {}", email, bu.getUser().getEmail(), businessId);
    }

    // ── Rules ────────────────────────────────────────────────────────────────

    private static List<UserRoles> assignableBy(BusinessUser actor) {
        if (TOP.contains(actor.getRoles())) return ASSIGNABLE;
        return ASSIGNABLE.stream().filter(FRONT_LINE::contains).toList();
    }

    private static boolean canManage(BusinessUser actor, BusinessUser target) {
        if (actor.getId().equals(target.getId())) return false;
        if (target.getRoles() == UserRoles.OWNER) return false;
        if (TOP.contains(actor.getRoles())) return true;
        return actor.getRoles() == UserRoles.MANAGER && FRONT_LINE.contains(target.getRoles());
    }

    private static void requireCanAssign(BusinessUser actor, UserRoles role) {
        if (!assignableBy(actor).contains(role)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can't give the " + role.name().toLowerCase() + " role");
        }
    }

    private BusinessUser requireManageable(BusinessUser actor, Long memberId, Long businessId) {
        BusinessUser bu = businessUserRepo.findById(memberId)
                .filter(m -> m.getBusiness().getId().equals(businessId) && m.isActiveMember())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Team member not found"));
        if (actor.getId().equals(bu.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can't change your own access. Ask another admin.");
        }
        if (!canManage(actor, bu)) throw new AppException(HttpStatus.FORBIDDEN, "You can't manage this person");
        return bu;
    }

    /** A company always keeps at least one owner or admin. */
    private void requireAnotherAdmin(BusinessUser leaving) {
        boolean another = businessUserRepo.findAllByBusiness(leaving.getBusiness()).stream()
                .anyMatch(m -> m.isActiveMember() && TOP.contains(m.getRoles()) && !m.getId().equals(leaving.getId()));
        if (!another) throw new AppException(HttpStatus.CONFLICT, "The company must keep at least one admin");
    }

    private List<Shop> requireShops(List<Long> ids, Long businessId) {
        if (ids == null) return List.of();
        return new LinkedHashSet<>(ids).stream().map(id -> accessHelper.requireShopInBusiness(id, businessId)).toList();
    }

    /** Makes the person's location assignments exactly {@code shops}. */
    private void syncLocations(BusinessUser bu, List<Shop> shops, String by) {
        Set<Long> wanted = new HashSet<>();
        shops.forEach(s -> wanted.add(s.getId()));
        List<ShopUsers> current = shopUsersRepo.findActiveByBusinessUser(bu);
        Set<Long> have = new HashSet<>();
        for (ShopUsers su : current) {
            if (su.getShop() == null || !wanted.contains(su.getShop().getId())) shopUsersRepo.delete(su);
            else have.add(su.getShop().getId());
        }
        for (Shop s : shops) {
            if (have.contains(s.getId())) continue;
            ShopUsers su = new ShopUsers();
            su.setBusinessUser(bu);
            su.setShop(s);
            su.setRoles(bu.getRoles());
            su.setDeleted(false);
            su.setCreatedBy(by);
            su.setCreatedAt(new Date());
            shopUsersRepo.save(su);
        }
    }

    private Member view(BusinessUser m, BusinessUser actor) {
        return view(m, actor, shopUsersRepo.findActiveByBusinessUser(m));
    }

    private Member view(BusinessUser m, BusinessUser actor, List<ShopUsers> assignments) {
        List<Location> locations = assignments.stream()
                .filter(su -> su.getShop() != null && !Boolean.TRUE.equals(su.getShop().getDeleted()))
                .map(su -> new Location(su.getShop().getId(), su.getShop().getShopName()))
                .sorted(Comparator.comparing(Location::name)).toList();
        User u = m.getUser();
        return new Member(m.getId(), u.getId(), UserAccounts.displayName(u), u.getEmail(), u.getTelephonePrimary(),
                m.getRoles(), locations, Boolean.TRUE.equals(u.getMustChangePassword()),
                m.getId().equals(actor.getId()), canManage(actor, m));
    }
}
