/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.annotation.Resource;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.tool.api.SessionManager;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/** WM02: create a single private application workspace backed by a Sakai project site.
 * No user-id or Sakai site-id is accepted from the browser. Native site/add and
 * site/add.project enforcement is preserved, rather than bypassed with an advisor.
 */
@Service
public class WorkspaceCreationService {
    static final String WORKSPACE_TYPE_PROPERTY = "bootstrap.workspace.kind";
    static final String WORKSPACE_VISIBILITY_PROPERTY = "bootstrap.workspace.visibility";

    @Resource private SiteService sites;
    @Resource private SessionManager sessions;

    public WorkspaceCreationService() { }
    WorkspaceCreationService(SiteService sites, SessionManager sessions) {
        this.sites = sites;
        this.sessions = sessions;
    }

    /** UI permission hint, not the final write permission check. */
    public boolean mayCreate() {
        String user = sessions.getCurrentSessionUserId();
        return user != null && !user.isBlank()
                && sites.allowAddProjectSite() && sites.allowAddSite(null);
    }

    public WorkspaceCreateResult create(WorkspaceCreateRequest request) {
        String userId = sessions.getCurrentSessionUserId();
        if (userId == null || userId.isBlank()) {
            throw new WorkspaceUnauthenticatedException();
        }
        String title = normalizeAndValidate(request);
        if (!sites.allowAddProjectSite() || !sites.allowAddSite(null)) {
            throw new WorkspaceCreationDeniedException();
        }

        // Non-guessable ID; do not derive from a user-supplied name.
        String id = "bootstrap-" + UUID.randomUUID();
        if (!sites.allowAddSite(id)) {
            throw new WorkspaceCreationDeniedException();
        }
        // Sakai performs its own authority checks again in addSite(). This is
        // deliberately NOT called inside a SecurityAdvisor or privileged context.
        try {
        Site site = sites.addSite(id, "project");
        // Work on the still-unpublished site; if provisioning fails, do not
        // publish a partially provisioned or potentially exposed workspace.
        site.setPublished(false);
        site.setJoinable(false);
        site.setTitle(title);
        site.setDescription(request.description() == null ? "" : request.description().trim());
        site.getPropertiesEdit().addProperty(WORKSPACE_TYPE_PROPERTY, "application");
        site.getPropertiesEdit().addProperty(WORKSPACE_VISIBILITY_PROPERTY, "private");
        // Give the new application workspace a real portal landing page.
        // The tool is already installed in WM01; no arbitrary tool IDs are accepted.
        SitePage landing = site.addPage();
        landing.setTitle("Overview");
        landing.addTool("sakai.bootstrap.workspaces");

        String ownerRole = site.getMaintainRole();
        if (ownerRole == null || ownerRole.isBlank() || site.getRole(ownerRole) == null) {
            // Fail closed: a missing/broken realm template must not publish an
            // ownerless site. An administrator can inspect the draft record.
            throw new IllegalStateException("Missing project workspace owner-role template");
        }
        if (site.getMember(userId) == null) {
            site.addMember(userId, ownerRole, true, false);
        } else if (!ownerRole.equals(site.getMember(userId).getRole().getId())) {
            throw new IllegalStateException("Creator was not assigned the workspace owner role");
        }

        // Project templates vary by installation. Deny Sakai's generic guest
        // roles access before a private workspace can be published.
        removeGuestVisitAccess(site, ".anon");
        removeGuestVisitAccess(site, ".auth");

        // First persist the safe unpublished state and verify owner membership.
        sites.save(site);
        Site persisted = sites.getSite(id);
        if (persisted.getMember(userId) == null
                || persisted.getMember(userId).getRole() == null
                || !ownerRole.equals(persisted.getMember(userId).getRole().getId())
                || persisted.isJoinable()) {
            throw new IllegalStateException("Workspace owner or private policy did not persist");
        }
        verifyGuestAccessDisabled(persisted, ".anon");
        verifyGuestAccessDisabled(persisted, ".auth");

        // Only after the owner and guest-role checks succeed can the private
        // workspace be published for its members.
        persisted.setPublished(true);
        sites.save(persisted);
        Site result = sites.getSite(id);
        if (!result.isPublished() || !sites.allowAccessSite(id)) {
            throw new IllegalStateException("Workspace was not accessible to its creator");
        }
        String portalUrl = "/portal/site/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
        return new WorkspaceCreateResult(id, title, portalUrl);
        } catch (PermissionException e) {
            throw new WorkspaceCreationDeniedException();
        } catch (IdUsedException e) {
            throw new IllegalStateException("Generated workspace ID collided; retry with a new request", e);
        } catch (IdInvalidException | IdUnusedException e) {
            throw new IllegalStateException("Sakai could not complete workspace provisioning", e);
        }
    }

    static String normalizeAndValidate(WorkspaceCreateRequest request) {
        if (request == null) throw new WorkspaceInvalidRequestException("Workspace details are required.");
        String name = request.name() == null ? "" : request.name().strip();
        if (name.length() < 3 || name.length() > 100 || hasControls(name)) {
            throw new WorkspaceInvalidRequestException("Name must be 3–100 characters without control characters.");
        }
        if (!"application".equals(request.type()) || !"private".equals(request.visibility())) {
            throw new WorkspaceInvalidRequestException("Only private application workspaces are supported in WM02.");
        }
        String description = request.description() == null ? "" : request.description();
        if (description.length() > 500 || hasControls(description)) {
            throw new WorkspaceInvalidRequestException("Description must be at most 500 characters without control characters.");
        }
        return name;
    }

    private static boolean hasControls(String s) {
        return s.codePoints().anyMatch(Character::isISOControl);
    }

    private static void removeGuestVisitAccess(Site site, String roleId) {
        Role role = site.getRole(roleId);
        if (role != null) {
            role.disallowFunction(SiteService.SITE_VISIT);
            role.disallowFunction(SiteService.SITE_VISIT_UNPUBLISHED);
        }
    }

    private static void verifyGuestAccessDisabled(Site site, String roleId) {
        Role role = site.getRole(roleId);
        if (role != null && (role.isAllowed(SiteService.SITE_VISIT)
                || role.isAllowed(SiteService.SITE_VISIT_UNPUBLISHED))) {
            throw new IllegalStateException("Private workspace template grants guest site access");
        }
    }
}
