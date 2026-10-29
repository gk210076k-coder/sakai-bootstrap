/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Test the real Spring-managed service with only Sakai kernel boundary mocks. */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = WorkspaceCatalogServiceTest.Wiring.class)
public class WorkspaceCatalogServiceTest {
    @Configuration
    static class Wiring {
        @Bean SiteService siteService() { return mock(SiteService.class); }
        @Bean SessionManager sessionManager() { return mock(SessionManager.class); }
        @Bean WorkspaceCatalogService workspaceCatalogService(SiteService sites, SessionManager sessions) {
            return new WorkspaceCatalogService(sites, sessions);
        }
    }
    @Autowired private SiteService sites;
    @Autowired private SessionManager sessions;
    @Autowired private WorkspaceCatalogService catalog;

    @Before public void resetMocks() { reset(sites, sessions); }

    @Test(expected = WorkspaceUnauthenticatedException.class)
    public void unauthenticatedRequestIsRejectedBeforeSiteQuery() {
        when(sessions.getCurrentSessionUserId()).thenReturn(null);
        try { catalog.listCurrentUserWorkspaces(); }
        finally { verify(sites, never()).getUserSites(anyBoolean(), anyBoolean()); }
    }

    @Test public void accessibleProjectIsListedAndEncoded() {
        when(sessions.getCurrentSessionUserId()).thenReturn("user-1");
        Site project = site("a site", "Customer Portal", "project", true);
        Role owner = mock(Role.class);
        when(owner.getId()).thenReturn("owner");
        when(project.getUserRole("user-1")).thenReturn(owner);
        when(sites.getUserSites(false, true)).thenReturn(List.of(project));
        when(sites.allowAccessSite("a site")).thenReturn(true);
        List<WorkspaceView> result = catalog.listCurrentUserWorkspaces();
        assertEquals(1, result.size());
        assertEquals("Customer Portal", result.get(0).title());
        assertEquals("owner", result.get(0).role());
        assertTrue(result.get(0).portalUrl().endsWith("a%20site"));
    }

    @Test public void restrictedAndAcademicSitesAreNotExposed() {
        when(sessions.getCurrentSessionUserId()).thenReturn("user-1");
        Site restricted = site("secret", "Secret", "project", true);
        Site course = site("course", "Course", "course", true);
        Site home = site("home", "Home", "project", true);
        Site allowed = site("allowed", "Application", "application", false);
        when(sites.getUserSites(false, true)).thenReturn(List.of(restricted, course, home, allowed));
        when(sites.isUserSite("home")).thenReturn(true);
        when(sites.allowAccessSite("secret")).thenReturn(false);
        when(sites.allowAccessSite("allowed")).thenReturn(true);
        List<WorkspaceView> visible = catalog.listCurrentUserWorkspaces();
        assertEquals(1, visible.size());
        assertEquals("allowed", visible.get(0).id());
        assertFalse(visible.get(0).published());
    }

    @Test public void emptySiteListDoesNotFail() {
        when(sessions.getCurrentSessionUserId()).thenReturn("user-1");
        when(sites.getUserSites(false, true)).thenReturn(List.of());
        assertTrue(catalog.listCurrentUserWorkspaces().isEmpty());
    }

    private Site site(String id, String title, String type, boolean published) {
        Site site = mock(Site.class);
        when(site.getId()).thenReturn(id);
        when(site.getTitle()).thenReturn(title);
        when(site.getType()).thenReturn(type);
        when(site.isPublished()).thenReturn(published);
        return site;
    }
}
