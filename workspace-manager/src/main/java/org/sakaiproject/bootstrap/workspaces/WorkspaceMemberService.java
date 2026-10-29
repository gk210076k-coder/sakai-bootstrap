/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import jakarta.annotation.Resource;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * WM03: manage memberships only in WM02 private application workspaces.
 * Sakai's site realm and SiteService checks remain authoritative. Never edit
 * global templates, synthetic users, or other site types through this service.
 */
@Service
public class WorkspaceMemberService {
    private static final Pattern WORKSPACE_ID = Pattern.compile(
            "bootstrap-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Set<String> GRANTABLE_ROLES = Set.of("maintain", "access");
    @Resource private SiteService sites;
    @Resource private SessionManager sessions;
    @Resource private UserDirectoryService users;
    @Resource private SecurityService security;

    public WorkspaceMemberService() { }
    WorkspaceMemberService(SiteService sites, SessionManager sessions, UserDirectoryService users,
                           SecurityService security) {
        this.sites = sites;
        this.sessions = sessions;
        this.users = users;
        this.security = security;
    }

    public record MemberRow(String userId, String role, boolean active, boolean canRemove) { }
    public record MemberPage(String siteId, String title, String portalUrl, List<MemberRow> members,
                             String currentUserId, boolean canManage) { }

    public MemberPage members(String siteId) {
        Site site = managedSite(siteId);
        String actor = actorId();
        if (!sites.allowAccessSite(siteId)) throw new WorkspaceMemberDeniedException();
        boolean canManage = canManage(site, actor);
        // Read-only member disclosure is limited to maintainers: normal members
        // need not see a list of account identifiers.
        if (!canManage) throw new WorkspaceMemberDeniedException();
        List<MemberRow> rows = site.getMembers().stream()
                .filter(m -> m != null && m.getRole() != null && m.getUserId() != null)
                .map(m -> new MemberRow(m.getUserId(), m.getRole().getId(), m.isActive(),
                        !m.getUserId().equals(actor) && !isProtectedTarget(site, m.getUserId())
                                && !isLastMaintainer(site, m)))
                .sorted(Comparator.comparing(MemberRow::userId))
                .toList();
        String url = "/portal/site/" + UriUtils.encodePathSegment(siteId, StandardCharsets.UTF_8);
        return new MemberPage(siteId, site.getTitle(), url, rows, actor, true);
    }

    /** Serializes writes within this JVM; Sakai remains responsible for realm persistence. */
    public synchronized void grant(String siteId, String target, String roleId) {
        Site site = editableSite(siteId);
        target = validateUserId(target);
        denyProtectedMutation(site, target);
        if (!GRANTABLE_ROLES.contains(roleId) || site.getRole(roleId) == null
                || !site.getRole(roleId).isAllowed(SiteService.SITE_VISIT)) {
            throw new WorkspaceMemberInvalidException("Choose an available workspace role with site visit permission.");
        }
        try {
            users.getUser(target); // Must exist in Sakai; no arbitrary/synthetic grants.
        } catch (UserNotDefinedException ex) {
            throw new WorkspaceMemberInvalidException("The specified Sakai user does not exist.");
        }
        if (target.equals(actorId()) && !"maintain".equals(roleId)) {
            throw new WorkspaceMemberDeniedException();
        }
        Member existing = site.getMember(target);
        if (existing != null && existing.isActive() && "maintain".equals(existing.getRole().getId())
                && !"maintain".equals(roleId) && isLastMaintainer(site, existing)) {
            throw new WorkspaceMemberInvalidException("Cannot demote the final active maintainer.");
        }
        site.addMember(target, roleId, true, false);
        save(site);
    }

    public synchronized void remove(String siteId, String target) {
        Site site = editableSite(siteId);
        target = validateUserId(target);
        denyProtectedMutation(site, target);
        if (target.equals(actorId())) throw new WorkspaceMemberDeniedException();
        Member member = site.getMember(target);
        if (member == null) throw new WorkspaceMemberInvalidException("User is not a workspace member.");
        if (isLastMaintainer(site, member)) {
            throw new WorkspaceMemberInvalidException("Cannot remove the final active maintainer.");
        }
        site.removeMember(target);
        save(site);
    }

    private Site editableSite(String id) {
        Site site = managedSite(id);
        if (!canManage(site, actorId())) throw new WorkspaceMemberDeniedException();
        return site;
    }

    /**
     * The original site creator is the protected workspace owner. Sakai security,
     * not a user-type or display-name comparison, identifies system admins.
     * Missing creator metadata fails closed until an administrator repairs it
     * outside this ordinary membership interface.
     */
    private boolean isProtectedTarget(Site site, String target) {
        User owner = site.getCreatedBy();
        if (owner == null || owner.getId() == null || owner.getId().isBlank()) return true;
        return target.equals(owner.getId())
                || UserDirectoryService.ADMIN_ID.equalsIgnoreCase(target)
                || security.isSuperUser(target);
    }

    private void denyProtectedMutation(Site site, String target) {
        if (isProtectedTarget(site, target)) {
            throw new WorkspaceMemberDeniedException();
        }
    }

    private boolean canManage(Site site, String actor) {
        Member member = site.getMember(actor);
        return member != null && member.isActive() && member.getRole() != null
                && site.getMaintainRole() != null
                && site.getMaintainRole().equals(member.getRole().getId())
                && sites.allowUpdateSiteMembership(site.getId());
    }

    private Site managedSite(String id) {
        actorId();
        if (id == null || !WORKSPACE_ID.matcher(id).matches()) throw new WorkspaceMemberDeniedException();
        try {
            Site site = sites.getSite(id);
            if (site == null || !"project".equals(site.getType()) || site.getProperties() == null
                    || !"application".equals(site.getProperties().getProperty(
                            WorkspaceCreationService.WORKSPACE_TYPE_PROPERTY))
                    || !"private".equals(site.getProperties().getProperty(
                            WorkspaceCreationService.WORKSPACE_VISIBILITY_PROPERTY))) {
                throw new WorkspaceMemberDeniedException();
            }
            return site;
        } catch (IdUnusedException ex) {
            throw new WorkspaceMemberDeniedException();
        }
    }

    private String actorId() {
        String id = sessions.getCurrentSessionUserId();
        if (id == null || id.isBlank()) throw new WorkspaceUnauthenticatedException();
        return id;
    }

    private static String validateUserId(String id) {
        if (id == null || id.isBlank() || id.length() > 99 || !id.equals(id.trim())
                || id.codePoints().anyMatch(Character::isISOControl) || id.startsWith(".")) {
            throw new WorkspaceMemberInvalidException("Enter a valid Sakai user ID.");
        }
        return id;
    }

    private static boolean isLastMaintainer(Site site, Member target) {
        if (!target.isActive() || target.getRole() == null
                || !site.getMaintainRole().equals(target.getRole().getId())) return false;
        return site.getMembers().stream().filter(m -> m.isActive() && m.getRole() != null
                && site.getMaintainRole().equals(m.getRole().getId())).count() <= 1;
    }

    private void save(Site site) {
        try { sites.saveSiteMembership(site); }
        catch (IdUnusedException | PermissionException ex) {
            throw new WorkspaceMemberDeniedException();
        }
    }
}
