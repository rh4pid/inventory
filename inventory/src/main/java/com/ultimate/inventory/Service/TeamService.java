package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.InvitationStatus;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.BusinessMembershipDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.InvitationRepository;
import com.ultimate.inventory.Repository.UserRepository;
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
    private final UserRepository userRepo;
    private final InvitationRepository invitationRepo;

    /** How long an invitation stays open. */
    static final long INVITE_DAYS = 14;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    public TeamService(BusinessAccessHelper accessHelper, BusinessUserRepo businessUserRepo,
                       ShopUsersRepository shopUsersRepo, UserAccounts accounts,
                       UserRepository userRepo, InvitationRepository invitationRepo) {
        this.userRepo = userRepo;
        this.invitationRepo = invitationRepo;
        this.accessHelper = accessHelper;
        this.businessUserRepo = businessUserRepo;
        this.shopUsersRepo = shopUsersRepo;
        this.accounts = accounts;
    }

    public record Location(Long id, String name) {}

    public record Member(Long id, Long userId, String username, String name, String email, String phone, UserRoles role,
                         List<Location> locations, boolean pendingFirstSignIn, boolean you, boolean canManage) {}

    public record Team(List<Member> members, List<UserRoles> rolesYouCanAssign, List<SentInvitation> invitations) {}

    /** Invite someone who already has an account, by their username. */
    public record Invite(String username, UserRoles role, List<Long> shopIds) {}

    /** An open invitation, as the company sees it. */
    public record SentInvitation(Long id, Long userId, String username, String name, UserRoles role,
                                 List<Location> locations, String invitedBy, Date sentAt, Date expiresAt, boolean canManage) {}

    /** An open invitation, as the invited person sees it. */
    public record MyInvitation(Long id, Long businessId, String businessName, String businessLogo, UserRoles role,
                               List<String> locations, String invitedBy, Date sentAt, Date expiresAt) {}

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
        return new Team(members, assignableBy(actor), openInvitations(business, actor));
    }

    private List<SentInvitation> openInvitations(Business business, BusinessUser actor) {
        Date now = new Date();
        return invitationRepo.findByBusinessAndStatusOrderByCreatedAtDesc(business, InvitationStatus.PENDING).stream()
                .filter(inv -> inv.isOpen(now))
                .map(inv -> sentView(inv, actor)).toList();
    }

    /** Invitations waiting for you to answer. */
    public List<MyInvitation> myInvitations(String email) {
        User me = accessHelper.requireUser(email);
        Date now = new Date();
        return invitationRepo.findByInviteeAndStatusOrderByCreatedAtDesc(me, InvitationStatus.PENDING).stream()
                .filter(inv -> inv.isOpen(now))
                .filter(inv -> usable(inv.getBusiness()))
                .map(inv -> new MyInvitation(inv.getId(), inv.getBusiness().getId(), inv.getBusiness().getName(),
                        inv.getBusiness().getBusinessLogo(), inv.getRole(),
                        locationsOf(inv).stream().map(Shop::getShopName).toList(),
                        UserAccounts.displayName(inv.getInvitedBy()), inv.getCreatedAt(), inv.getExpiresAt()))
                .toList();
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /**
     * Invites someone who already has an account. They join only when they accept.
     * Inviting someone again while an invitation is open updates it (role, locations, expiry).
     */
    @Transactional
    public SentInvitation invite(Long businessId, Invite req, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        Business business = accessHelper.requireBusiness(businessId);
        if (req == null) throw new AppException(HttpStatus.BAD_REQUEST, "Enter a username");
        UserRoles role = req.role() != null ? req.role() : UserRoles.CASHIER;
        requireCanAssign(actor, role);
        List<Shop> shops = requireShops(req.shopIds(), businessId);
        User invitee = requireInvitable(req.username());
        return sentView(openInvitation(business, invitee, actor, role, shops, email), actor);
    }

    /** The person must already have an account. */
    User requireInvitable(String rawUsername) {
        String username = Usernames.clean(rawUsername);
        if (username.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Enter a username");
        User invitee = userRepo.findByUsername(username);
        if (invitee == null || Boolean.TRUE.equals(invitee.getDeleted()) || Boolean.TRUE.equals(invitee.getSuperAdmin())) {
            throw new AppException(HttpStatus.NOT_FOUND, "No one has the username @" + username);
        }
        return invitee;
    }

    /** Creates (or refreshes) the open invitation for this person. */
    Invitation openInvitation(Business business, User invitee, BusinessUser actor, UserRoles role, List<Shop> shops, String by) {
        if (invitee.getId().equals(actor.getUser().getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "You're already in this company");
        }
        BusinessUser existing = businessUserRepo.findByBusinessAndUser(business, invitee);
        if (existing != null && existing.isActiveMember()) {
            throw new AppException(HttpStatus.CONFLICT, UserAccounts.displayName(invitee) + " is already on the team");
        }
        Date now = new Date();
        Invitation inv = invitationRepo.findFirstByBusinessAndInviteeAndStatus(business, invitee, InvitationStatus.PENDING);
        if (inv == null) {
            inv = new Invitation();
            inv.setBusiness(business);
            inv.setInvitee(invitee);
            inv.setStatus(InvitationStatus.PENDING);
            inv.setCreatedBy(by);
        } else {
            inv.setUpdatedBy(by);
            inv.setUpdatedAt(now);
        }
        inv.setInvitedBy(actor.getUser());
        inv.setRole(role);
        inv.setLocationIdList(shops.stream().map(Shop::getId).toList());
        inv.setCreatedAt(now); // the "sent" date: a re-sent invitation counts from now
        inv.setExpiresAt(new Date(now.getTime() + INVITE_DAYS * DAY_MS));
        inv = invitationRepo.save(inv);
        log.info("{} invited @{} as {} to business {}", by, invitee.getUsername(), role, business.getCode());
        return inv;
    }

    /** Sends the invitation again: open for another INVITE_DAYS days. */
    @Transactional
    public SentInvitation resendInvitation(Long businessId, Long invitationId, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        Invitation inv = requireCompanyInvitation(actor, invitationId, businessId);
        Date now = new Date();
        inv.setCreatedAt(now);
        inv.setExpiresAt(new Date(now.getTime() + INVITE_DAYS * DAY_MS));
        inv.setUpdatedBy(email);
        inv.setUpdatedAt(now);
        return sentView(invitationRepo.save(inv), actor);
    }

    @Transactional
    public void cancelInvitation(Long businessId, Long invitationId, String email) {
        BusinessUser actor = accessHelper.requireManager(businessId, email);
        Invitation inv = requireCompanyInvitation(actor, invitationId, businessId);
        inv.setStatus(InvitationStatus.CANCELLED);
        inv.setRespondedAt(new Date());
        inv.setUpdatedBy(email);
        inv.setUpdatedAt(new Date());
        invitationRepo.save(inv);
    }

    /** You join the company with the role and locations in the invitation. */
    @Transactional
    public BusinessMembershipDTO acceptInvitation(Long invitationId, String email) {
        User me = accessHelper.requireUser(email);
        Invitation inv = requireMyOpenInvitation(me, invitationId);
        Business business = inv.getBusiness();
        if (!usable(business)) {
            throw new AppException(HttpStatus.CONFLICT, business.getName() + " can't take new people right now");
        }
        BusinessUser bu = businessUserRepo.findByBusinessAndUser(business, me);
        if (bu == null || !bu.isActiveMember()) {
            if (bu == null) {
                bu = new BusinessUser();
                bu.setBusiness(business);
                bu.setUser(me);
                bu.setCreatedBy(email);
                bu.setCreatedAt(new Date());
            } else {
                bu.setUpdatedBy(email);
                bu.setUpdatedAt(new Date());
            }
            bu.setRoles(inv.getRole());
            bu.setActive(true);
            bu = businessUserRepo.save(bu);
            syncLocations(bu, locationsOf(inv), email);
        }
        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setRespondedAt(new Date());
        invitationRepo.save(inv);
        log.info("@{} joined business {} as {}", me.getUsername(), business.getCode(), inv.getRole());
        return new BusinessMembershipDTO(bu);
    }

    @Transactional
    public void declineInvitation(Long invitationId, String email) {
        User me = accessHelper.requireUser(email);
        Invitation inv = requireMyOpenInvitation(me, invitationId);
        inv.setStatus(InvitationStatus.DECLINED);
        inv.setRespondedAt(new Date());
        invitationRepo.save(inv);
    }

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

    // ── Invitation helpers ───────────────────────────────────────────────────

    private Invitation requireCompanyInvitation(BusinessUser actor, Long invitationId, Long businessId) {
        Invitation inv = invitationRepo.findById(invitationId)
                .filter(i -> i.getBusiness().getId().equals(businessId) && i.getStatus() == InvitationStatus.PENDING)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invitation not found"));
        requireCanAssign(actor, inv.getRole()); // managers handle invitations for the roles they can give
        return inv;
    }

    private Invitation requireMyOpenInvitation(User me, Long invitationId) {
        Invitation inv = invitationRepo.findById(invitationId)
                .filter(i -> i.getInvitee().getId().equals(me.getId()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invitation not found"));
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "This invitation was " + inv.getStatus().name().toLowerCase());
        }
        if (!inv.isOpen(new Date())) {
            inv.setStatus(InvitationStatus.EXPIRED);
            invitationRepo.save(inv);
            throw new AppException(HttpStatus.CONFLICT, "This invitation has expired. Ask them to send it again.");
        }
        return inv;
    }

    private static boolean usable(Business b) {
        return !Boolean.TRUE.equals(b.getDeleted()) && !Boolean.TRUE.equals(b.getSuspended());
    }

    /** The invitation's locations that still exist. */
    private List<Shop> locationsOf(Invitation inv) {
        List<Shop> shops = new ArrayList<>();
        for (Long id : inv.locationIdList()) {
            try {
                Shop shop = accessHelper.requireShopInBusiness(id, inv.getBusiness().getId());
                if (!Boolean.TRUE.equals(shop.getDeleted())) shops.add(shop);
            } catch (AppException gone) {
                // Location removed since the invitation was sent.
            }
        }
        return shops;
    }

    private SentInvitation sentView(Invitation inv, BusinessUser actor) {
        User u = inv.getInvitee();
        List<Location> locations = locationsOf(inv).stream()
                .map(shop -> new Location(shop.getId(), shop.getShopName())).toList();
        return new SentInvitation(inv.getId(), u.getId(), u.getUsername(), UserAccounts.displayName(u), inv.getRole(),
                locations, UserAccounts.displayName(inv.getInvitedBy()), inv.getCreatedAt(), inv.getExpiresAt(),
                assignableBy(actor).contains(inv.getRole()));
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
        return new Member(m.getId(), u.getId(), u.getUsername(), UserAccounts.displayName(u), u.getEmail(), u.getTelephonePrimary(),
                m.getRoles(), locations, Boolean.TRUE.equals(u.getMustChangePassword()),
                m.getId().equals(actor.getId()), canManage(actor, m));
    }
}
