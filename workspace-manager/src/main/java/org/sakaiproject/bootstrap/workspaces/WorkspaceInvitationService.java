/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.annotation.Resource;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.springframework.stereotype.Service;

/**
 * WM04: durable, targeted invitations for private WM02 workspaces. A bearer
 * token alone never grants membership: an authenticated, exact intended user
 * must explicitly accept the still-pending invitation.
 */
@Service
public class WorkspaceInvitationService {
    private static final Pattern SITE_ID = Pattern.compile("bootstrap-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final Set<String> ALLOWED_ROLES = Set.of("access", "maintain");
    private static final long TTL_SECONDS = 7 * 24 * 60 * 60;
    @Resource private WorkspaceInvitationStore store;
    @Resource private WorkspaceMemberService members;
    @Resource private SiteService sites;
    @Resource private SessionManager sessions;
    @Resource private UserDirectoryService users;
    @Resource private SecurityService security;
    private final Clock clock;

    public WorkspaceInvitationService() { this(Clock.systemUTC()); }
    WorkspaceInvitationService(Clock clock) { this.clock = clock; }

    public record InvitationLink(String id, String secret, long expiresAt) { }
    public record InvitationRow(String id, String invitee, String inviter, String role,
                                String status, long expiresAt, boolean revocable) { }
    public record ManagePage(String siteId, String title, List<InvitationRow> invitations) { }
    public record RespondPage(String siteId, String siteTitle, String role,
                              String inviter, String target, String status) { }

    public ManagePage manage(String siteId) {
        var authorized = members.members(siteId); // Explicit WM03 manager-only read gate.
        long now = now();
        List<InvitationRow> rows = store.list(siteId).stream()
                .map(inv -> new InvitationRow(inv.id(), inv.invitee(), inv.inviter(), inv.role(),
                        effectiveStatus(inv, now), inv.expiresAt(),
                        "PENDING".equals(inv.status()) && !inv.expired(now))).toList();
        return new ManagePage(siteId, authorized.title(), rows);
    }

    public synchronized InvitationLink issue(String siteId, String invitee, String role) {
        members.members(siteId); // Gate permission before any user existence or invitation disclosure.
        String actor = actor();
        final String invitedUser = validUserId(invitee);
        if (actor.equals(invitedUser)) throw new WorkspaceInvitationInvalidException("You are already a workspace member.");
        if (!ALLOWED_ROLES.contains(role)) throw new WorkspaceInvitationInvalidException("Choose access or maintain.");
        Site site = privateWorkspace(siteId);
        if (site.getCreatedBy() == null || site.getCreatedBy().getId() == null
                || site.getCreatedBy().getId().isBlank()) throw new WorkspaceInvitationDeniedException();
        if (site.getCreatedBy().getId().equals(invitedUser)
                || UserDirectoryService.ADMIN_ID.equalsIgnoreCase(invitedUser)
                || security.isSuperUser(invitedUser)) throw new WorkspaceInvitationDeniedException();
        if (site.getRole(role) == null || !site.getRole(role).isAllowed(SiteService.SITE_VISIT))
            throw new WorkspaceInvitationInvalidException("This site role does not permit workspace access.");
        try { users.getUser(invitedUser); }
        catch (UserNotDefinedException ex) {
            throw new WorkspaceInvitationInvalidException("The Sakai user ID does not exist.");
        }
        if (site.getMember(invitedUser) != null)
            throw new WorkspaceInvitationInvalidException("The user already has a workspace membership.");
        long now = now();
        boolean pending = store.hasPending(siteId, invitedUser, now);
        if (pending) throw new WorkspaceInvitationInvalidException("A pending invitation already exists for this user.");
        String rawToken = WorkspaceInvitationToken.generate();
        String id = UUID.randomUUID().toString();
        store.insert(new WorkspaceInvitationStore.Invitation(id, siteId, invitedUser, actor, role,
                WorkspaceInvitationToken.hash(rawToken), "PENDING", now, now + TTL_SECONDS));
        return new InvitationLink(id, rawToken, now + TTL_SECONDS);
    }

    public synchronized void revoke(String siteId, String invitationId) {
        members.members(siteId);
        if (invitationId == null || !invitationId.matches("[0-9a-f-]{36}"))
            throw new WorkspaceInvitationInvalidException("Invalid invitation ID.");
        var inv = store.findBySiteAndId(siteId, invitationId);
        if (inv == null) throw new WorkspaceInvitationInvalidException("Invitation does not exist.");
        if (!store.transition(inv.id(), "PENDING", "REVOKED"))
            throw new WorkspaceInvitationInvalidException("Invitation is no longer pending.");
    }

    /** GET only previews, never accepts an invitation or writes membership. */
    public RespondPage preview(String token) {
        var inv = findToken(token);
        requireInvitee(inv);
        Site site = privateWorkspace(inv.siteId());
        return new RespondPage(inv.siteId(), site.getTitle(), inv.role(), inv.inviter(),
                inv.invitee(), effectiveStatus(inv, now()));
    }

    public synchronized void decline(String token) {
        var inv = findToken(token);
        requireInvitee(inv);
        requirePending(inv);
        if (!store.transition(inv.id(), "PENDING", "DECLINED"))
            throw new WorkspaceInvitationInvalidException("Invitation was already used or revoked.");
    }

    public synchronized String accept(String token) {
        var inv = findToken(token);
        String current = requireInvitee(inv);
        requirePending(inv);
        Site check = privateWorkspace(inv.siteId());
        validateAcceptance(check, inv, current);
        // Atomic DB compare-and-swap: only one caller may claim this invitation.
        if (!store.claim(inv.id(), now()))
            throw new WorkspaceInvitationInvalidException("Invitation was already used or expired.");
        boolean membershipSaved = false;
        try {
            // Re-fetch current Sakai state after the claim; never replace an existing role.
            Site site = privateWorkspace(inv.siteId());
            validateAcceptance(site, inv, current);
            // Explicit, tightly-scoped advisor only during the single membership save.
            // It does NOT permit site.upd or any permissions on another site.
            final String siteReference = site.getReference();
            SecurityAdvisor advisor = (userId, function, reference) ->
                    current.equals(userId)
                    && SiteService.SECURE_UPDATE_SITE_MEMBERSHIP.equals(function)
                    && siteReference.equals(reference)
                    ? SecurityAdvisor.SecurityAdvice.ALLOWED
                    : SecurityAdvisor.SecurityAdvice.PASS;
            site.addMember(current, inv.role(), true, false);
            security.pushAdvisor(advisor);
            try {
                sites.saveSiteMembership(site);
                membershipSaved = true;
            } finally {
                security.popAdvisor(advisor);
            }
            if (!store.transition(inv.id(), "CLAIMED", "ACCEPTED"))
                throw new IllegalStateException("Membership persisted, invitation finalization requires administrator review");
            return inv.siteId();
        } catch (IdUnusedException | PermissionException ex) {
            throw new WorkspaceInvitationDeniedException();
        } finally {
            // If the database or Sakai write fails, the invitation can be retried.
            // If Sakai persisted but the final DB write failed, leave CLAIMED
            // (fail-closed), rather than allowing an unsafe replay.
            if (!membershipSaved) store.transition(inv.id(), "CLAIMED", "PENDING");
        }
    }

    private void validateAcceptance(Site site, WorkspaceInvitationStore.Invitation inv, String user) {
        if (!user.equals(inv.invitee()) || security.isSuperUser(user)
                || UserDirectoryService.ADMIN_ID.equalsIgnoreCase(user)
                || site.getCreatedBy() == null || site.getCreatedBy().getId() == null
                || site.getCreatedBy().getId().isBlank() || user.equals(site.getCreatedBy().getId()))
            throw new WorkspaceInvitationDeniedException();
        if (site.getMember(user) != null)
            throw new WorkspaceInvitationInvalidException("Membership already exists; invitation cannot overwrite its role.");
        if (!ALLOWED_ROLES.contains(inv.role()) || site.getRole(inv.role()) == null
                || !site.getRole(inv.role()).isAllowed(SiteService.SITE_VISIT))
            throw new WorkspaceInvitationInvalidException("The invited role is no longer available.");
    }

    private Site privateWorkspace(String id) {
        actor();
        if (id == null || !SITE_ID.matcher(id).matches()) throw new WorkspaceInvitationDeniedException();
        try {
            Site site = sites.getSite(id);
            if (site == null || site.isSoftlyDeleted() || !site.isPublished() || site.isJoinable()
                    || !"project".equals(site.getType()) || site.getProperties() == null
                    || !"application".equals(site.getProperties().getProperty(
                        WorkspaceCreationService.WORKSPACE_TYPE_PROPERTY))
                    || !"private".equals(site.getProperties().getProperty(
                        WorkspaceCreationService.WORKSPACE_VISIBILITY_PROPERTY)))
                throw new WorkspaceInvitationDeniedException();
            return site;
        } catch (IdUnusedException ex) {
            throw new WorkspaceInvitationDeniedException();
        }
    }

    private WorkspaceInvitationStore.Invitation findToken(String token) {
        var invitation = store.findByHash(WorkspaceInvitationToken.hash(token));
        if (invitation == null) throw new WorkspaceInvitationInvalidException("Invalid or expired invitation.");
        return invitation;
    }
    private String requireInvitee(WorkspaceInvitationStore.Invitation invite) {
        String current = actor();
        if (!current.equals(invite.invitee())) throw new WorkspaceInvitationDeniedException();
        return current;
    }
    private void requirePending(WorkspaceInvitationStore.Invitation inv) {
        if (!"PENDING".equals(inv.status()) || inv.expired(now()))
            throw new WorkspaceInvitationInvalidException("Invitation expired, revoked, or already answered.");
    }
    private String actor() {
        String userId = sessions.getCurrentSessionUserId();
        if (userId == null || userId.isBlank()) throw new WorkspaceUnauthenticatedException();
        return userId;
    }
    private static String validUserId(String id) {
        if (id == null || id.isBlank() || id.length() > 99 || !id.equals(id.trim())
                || id.startsWith(".") || id.codePoints().anyMatch(Character::isISOControl))
            throw new WorkspaceInvitationInvalidException("Enter an existing Sakai user ID.");
        return id;
    }
    private long now() { return clock.instant().getEpochSecond(); }
    private static String effectiveStatus(WorkspaceInvitationStore.Invitation inv, long now) {
        return "PENDING".equals(inv.status()) && inv.expired(now) ? "EXPIRED" : inv.status();
    }
}
