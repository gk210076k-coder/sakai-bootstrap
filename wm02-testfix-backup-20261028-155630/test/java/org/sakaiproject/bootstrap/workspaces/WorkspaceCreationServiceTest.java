/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WorkspaceCreationServiceTest {
    private SiteService sites;
    private SessionManager sessions;
    private WorkspaceCreationService service;
    @Before public void setUp() {
        sites = mock(SiteService.class);
        sessions = mock(SessionManager.class);
        service = new WorkspaceCreationService(sites, sessions);
        when(sessions.getCurrentSessionUserId()).thenReturn("account1");
        when(sites.allowAddProjectSite()).thenReturn(true);
        when(sites.allowAddSite(any())).thenReturn(true);
    }
    private WorkspaceCreateRequest valid() {
        return new WorkspaceCreateRequest("  Customer Portal  ", "application", "private", "Internal portal");
    }

    @Test public void createsPrivateWorkspaceWithOwnerAndLandingPage() throws Exception {
        Site site = mock(Site.class);
        SitePage page = mock(SitePage.class);
        Role owner = mock(Role.class);
        Member member = mock(Member.class);
        ResourcePropertiesEdit props = mock(ResourcePropertiesEdit.class);
        when(site.getMaintainRole()).thenReturn("maintain");
        when(site.getRole("maintain")).thenReturn(owner);
        when(site.getPropertiesEdit()).thenReturn(props);
        when(site.addPage()).thenReturn(page);
        when(site.getMember("account1")).thenReturn(null, member, member);
        when(member.getRole()).thenReturn(owner);
        when(owner.getId()).thenReturn("maintain");
        when(sites.addSite(anyString(), eq("project"))).thenReturn(site);
        when(sites.getSite(anyString())).thenReturn(site);
        when(sites.allowAccessSite(anyString())).thenReturn(true);
        when(site.isPublished()).thenReturn(true);
        WorkspaceCreateResult result = service.create(valid());
        assertEquals("Customer Portal", result.title());
        assertTrue(result.id().startsWith("bootstrap-"));
        verify(site).setJoinable(false);
        verify(site).setTitle("Customer Portal");
        verify(site).addMember("account1", "maintain", true, false);
        verify(site, atLeastOnce()).setPublished(false);
        verify(site).setPublished(true);
        verify(page).addTool("sakai.bootstrap.workspaces");
        verify(props).addProperty("bootstrap.workspace.kind", "application");
        verify(props).addProperty("bootstrap.workspace.visibility", "private");
        verify(sites, times(2)).save(site);
    }

    @Test(expected = WorkspaceCreationDeniedException.class)
    public void deniesIfNativeSitePermissionIsMissing() throws Exception {
        when(sites.allowAddProjectSite()).thenReturn(false);
        try { service.create(valid()); }
        finally { verify(sites, never()).addSite(anyString(), anyString()); }
    }
    @Test(expected = WorkspaceUnauthenticatedException.class)
    public void deniesAnonymousCreator() {
        when(sessions.getCurrentSessionUserId()).thenReturn(null);
        service.create(valid());
    }
    @Test(expected = WorkspaceInvalidRequestException.class)
    public void disallowsPublicVisibility() {
        service.create(new WorkspaceCreateRequest("Portal", "application", "public", ""));
    }
    @Test(expected = WorkspaceInvalidRequestException.class)
    public void disallowsAcademicSiteTypes() {
        service.create(new WorkspaceCreateRequest("Course", "course", "private", ""));
    }
    @Test(expected = WorkspaceInvalidRequestException.class)
    public void disallowsControlCharacters() {
        service.create(new WorkspaceCreateRequest("bad\nname", "application", "private", ""));
    }
    @Test(expected = IllegalStateException.class)
    public void neverPublishesWithoutOwnerTemplate() throws Exception {
        Site site = mock(Site.class);
        when(sites.addSite(anyString(), eq("project"))).thenReturn(site);
        try { service.create(valid()); }
        finally { verify(site, never()).setPublished(true); }
    }
    @Test(expected = IllegalStateException.class)
    public void neverPublishesIfGuestStillHasVisitAccess() throws Exception {
        Site site = mock(Site.class);
        SitePage page = mock(SitePage.class);
        Role owner = mock(Role.class);
        Role guest = mock(Role.class);
        Member member = mock(Member.class);
        when(site.getPropertiesEdit()).thenReturn(mock(ResourcePropertiesEdit.class));
        when(site.addPage()).thenReturn(page);
        when(site.getMaintainRole()).thenReturn("maintain");
        when(site.getRole("maintain")).thenReturn(owner);
        when(site.getRole(".anon")).thenReturn(guest);
        when(guest.isAllowed(SiteService.SITE_VISIT)).thenReturn(true);
        when(site.getMember("account1")).thenReturn(null, member, member);
        when(member.getRole()).thenReturn(owner);
        when(owner.getId()).thenReturn("maintain");
        when(sites.addSite(anyString(), eq("project"))).thenReturn(site);
        when(sites.getSite(anyString())).thenReturn(site);
        try { service.create(valid()); }
        finally { verify(site, never()).setPublished(true); }
    }
}
