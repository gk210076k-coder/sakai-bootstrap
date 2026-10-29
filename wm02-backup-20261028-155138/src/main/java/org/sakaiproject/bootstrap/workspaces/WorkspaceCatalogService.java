/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jakarta.annotation.Resource;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * WM01 is READ ONLY. Query only Sakai's current-user site list and check the
 * visit permission again before releasing any site details to the UI.
 * Do not accept a userId or siteId from a client to drive this listing.
 */
@Service
public class WorkspaceCatalogService {
    @Resource
    private SiteService siteService;
    @Resource
    private SessionManager sessionManager;

    // Required by Sakai Spring component injection (same pattern as Dashboard).
    public WorkspaceCatalogService() { }

    // Explicit seam for tests; production uses @Resource on fields.
    WorkspaceCatalogService(SiteService siteService, SessionManager sessionManager) {
        this.siteService = siteService;
        this.sessionManager = sessionManager;
    }

    public List<WorkspaceView> listCurrentUserWorkspaces() {
        String userId = sessionManager.getCurrentSessionUserId();
        if (userId == null || userId.isBlank()) {
            throw new WorkspaceUnauthenticatedException();
        }
        List<WorkspaceView> result = new ArrayList<>();
        // Sakai performs the current-user site selection. Include drafts only
        // where the current user can access them (e.g. as an owner).
        List<Site> userSites = siteService.getUserSites(false, true);
        if (userSites == null) {
            return List.of();
        }
        for (Site site : userSites) {
            if (site == null || site.getId() == null) {
                continue;
            }
            String id = site.getId();
            if (siteService.isUserSite(id) || !isWorkspaceType(site.getType())
                    || !siteService.allowAccessSite(id)) {
                continue;
            }
            Role membership = site.getUserRole(userId);
            String role = membership != null ? membership.getId() : "access granted";
            String name = site.getTitle();
            if (name == null || name.isBlank()) {
                name = id;
            }
            String type = site.getType() == null ? "project" : site.getType().toLowerCase(Locale.ROOT);
            String url = "/portal/site/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
            result.add(new WorkspaceView(id, name, type, role, site.isPublished(), url));
        }
        return List.copyOf(result);
    }

    static boolean isWorkspaceType(String type) {
        return "project".equalsIgnoreCase(type) || "application".equalsIgnoreCase(type);
    }
}
