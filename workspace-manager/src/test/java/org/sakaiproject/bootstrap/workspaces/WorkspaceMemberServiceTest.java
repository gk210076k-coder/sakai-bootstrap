/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.User;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WorkspaceMemberServiceTest {
    private final String id = "bootstrap-7d178d5f-c2c7-41c8-84e7-9ae41955a8e5";
    private SiteService sites;
    private SessionManager sessions;
    private UserDirectoryService users;
    private SecurityService security;
    private Site site;
    private Role maintain;
    private Role access;
    private Member owner;
    private WorkspaceMemberService service;

    @Before public void setup() throws Exception {
        sites = mock(SiteService.class);
        sessions = mock(SessionManager.class);
        users = mock(UserDirectoryService.class);
        security = mock(SecurityService.class);
        site = mock(Site.class);
        maintain = mock(Role.class);
        access = mock(Role.class);
        owner = member("admin", maintain);
        ResourceProperties props = mock(ResourceProperties.class);
        when(props.getProperty("bootstrap.workspace.kind")).thenReturn("application");
        when(props.getProperty("bootstrap.workspace.visibility")).thenReturn("private");
        when(site.getProperties()).thenReturn(props);
        User creator = mock(User.class);
        when(creator.getId()).thenReturn("admin");
        when(site.getCreatedBy()).thenReturn(creator);
        when(site.getId()).thenReturn(id);
        when(site.getTitle()).thenReturn("Customer Portal");
        when(site.getType()).thenReturn("project");
        when(site.getMaintainRole()).thenReturn("maintain");
        when(site.getRole("maintain")).thenReturn(maintain);
        when(site.getRole("access")).thenReturn(access);
        when(maintain.getId()).thenReturn("maintain");
        when(access.getId()).thenReturn("access");
        when(access.isAllowed(SiteService.SITE_VISIT)).thenReturn(true);
        when(maintain.isAllowed(SiteService.SITE_VISIT)).thenReturn(true);
        when(site.getMember("admin")).thenReturn(owner);
        when(site.getMembers()).thenReturn(Set.of(owner));
        when(sites.getSite(id)).thenReturn(site);
        when(sites.allowAccessSite(id)).thenReturn(true);
        when(sites.allowUpdateSiteMembership(id)).thenReturn(true);
        when(sessions.getCurrentSessionUserId()).thenReturn("admin");
        when(users.getUser("alice")).thenReturn(mock(User.class));
        service = new WorkspaceMemberService(sites, sessions, users, security);
    }

    private Member member(String user, Role role) {
        Member m = mock(Member.class);
        when(m.getUserId()).thenReturn(user);
        when(m.getRole()).thenReturn(role);
        when(m.isActive()).thenReturn(true);
        return m;
    }

    @Test public void listsOnlyMembersOfAuthorizedPrivateWorkspace() {
        WorkspaceMemberService.MemberPage page = service.members(id);
        assertEquals("Customer Portal", page.title());
        assertEquals(1, page.members().size());
        assertEquals("admin", page.members().get(0).userId());
        assertFalse(page.members().get(0).canRemove());
    }

    @Test public void addsSakaiAccountAsAccessMember() throws Exception {
        service.grant(id, "alice", "access");
        verify(users).getUser("alice");
        verify(site).addMember("alice", "access", true, false);
        verify(sites).saveSiteMembership(site);
    }

    @Test(expected = WorkspaceMemberDeniedException.class)
    public void rejectsUserWithoutNativeMembershipPermission() {
        when(sites.allowUpdateSiteMembership(id)).thenReturn(false);
        service.grant(id, "alice", "access");
    }

    @Test(expected = WorkspaceMemberDeniedException.class)
    public void rejectsNonmaintainer() {
        Member nonMaintainer = member("admin", access);
        when(site.getMember("admin")).thenReturn(nonMaintainer);
        service.grant(id, "alice", "maintain");
    }

    @Test(expected = WorkspaceMemberDeniedException.class)
    public void rejectsForeignSiteIds() {
        service.grant("!admin", "alice", "maintain");
    }

    @Test(expected = WorkspaceMemberDeniedException.class)
    public void rejectsWorkspaceWithoutPrivateMarker() {
        when(site.getProperties().getProperty("bootstrap.workspace.visibility")).thenReturn("public");
        service.grant(id, "alice", "access");
    }

    @Test(expected = WorkspaceMemberDeniedException.class)
    public void preventsSelfRemoval() {
        service.remove(id, "admin");
    }

    @Test(expected = WorkspaceMemberDeniedException.class)
    public void preventsSelfDemotion() {
        service.grant(id, "admin", "access");
    }

    @Test(expected = WorkspaceMemberInvalidException.class)
    public void rejectsUnrecognizedRoles() {
        service.grant(id, "alice", ".auth");
    }

    @Test(expected = WorkspaceMemberInvalidException.class)
    public void rejectsUnknownUser() throws Exception {
        when(users.getUser("alice")).thenThrow(new org.sakaiproject.user.api.UserNotDefinedException("alice"));
        service.grant(id, "alice", "access");
    }

    @Test public void removesOtherMember() throws Exception {
        Member alice = member("alice", access);
        when(site.getMember("alice")).thenReturn(alice);
        when(site.getMembers()).thenReturn(Set.of(owner, alice));
        service.remove(id, "alice");
        verify(site).removeMember("alice");
        verify(sites).saveSiteMembership(site);
    }

    @Test(expected = WorkspaceMemberInvalidException.class)
    public void protectsFinalMaintainerEvenIfOtherUserManages() {
        // Last-maintainer guard is also enforced independently of the UI.
        Member lastOwner = member("owner", maintain);
        when(site.getMembers()).thenReturn(Set.of(lastOwner));
        when(site.getMember("owner")).thenReturn(lastOwner);
        service.remove(id, "owner");
    }

    @Test public void systemAdministratorCannotBeRemovedBySecondMaintainer() throws Exception {
        Member bob = member("bob", maintain);
        when(sessions.getCurrentSessionUserId()).thenReturn("bob");
        when(site.getMember("bob")).thenReturn(bob);
        when(site.getMembers()).thenReturn(Set.of(owner, bob));
        try {
            service.remove(id, "admin");
            fail("A second maintainer must not remove system admin.");
        } catch (WorkspaceMemberDeniedException expected) { }
        verify(site, never()).removeMember("admin");
        verify(sites, never()).saveSiteMembership(any(Site.class));
    }

    @Test public void systemAdministratorCannotBeDemotedBySecondMaintainer() throws Exception {
        Member bob = member("bob", maintain);
        when(sessions.getCurrentSessionUserId()).thenReturn("bob");
        when(site.getMember("bob")).thenReturn(bob);
        when(site.getMembers()).thenReturn(Set.of(owner, bob));
        try {
            service.grant(id, "admin", "access");
            fail("A second maintainer must not demote system admin.");
        } catch (WorkspaceMemberDeniedException expected) { }
        verify(site, never()).addMember(eq("admin"), anyString(), anyBoolean(), anyBoolean());
        verify(sites, never()).saveSiteMembership(any(Site.class));
    }

    @Test public void superuserOtherThanAdminCannotBeRemovedOrDemoted() throws Exception {
        Member bob = member("bob", maintain);
        Member otherRoot = member("root2", maintain);
        when(sessions.getCurrentSessionUserId()).thenReturn("bob");
        when(site.getMember("bob")).thenReturn(bob);
        when(site.getMember("root2")).thenReturn(otherRoot);
        when(site.getMembers()).thenReturn(Set.of(owner, bob, otherRoot));
        when(security.isSuperUser("root2")).thenReturn(true);
        try { service.remove(id, "root2"); fail("Superuser removal should fail"); }
        catch (WorkspaceMemberDeniedException expected) { }
        try { service.grant(id, "root2", "access"); fail("Superuser demotion should fail"); }
        catch (WorkspaceMemberDeniedException expected) { }
        verify(site, never()).removeMember("root2");
        verify(site, never()).addMember(eq("root2"), anyString(), anyBoolean(), anyBoolean());
        verify(sites, never()).saveSiteMembership(any(Site.class));
    }

    @Test public void originalWorkspaceCreatorProtectedEvenWithOtherMaintainers() throws Exception {
        User originalCreator = mock(User.class);
        when(originalCreator.getId()).thenReturn("alice");
        when(site.getCreatedBy()).thenReturn(originalCreator);
        Member alice = member("alice", maintain);
        Member bob = member("bob", maintain);
        when(sessions.getCurrentSessionUserId()).thenReturn("bob");
        when(site.getMember("bob")).thenReturn(bob);
        when(site.getMember("alice")).thenReturn(alice);
        when(site.getMembers()).thenReturn(Set.of(alice, bob));
        try { service.remove(id, "alice"); fail("Creator removal should fail"); }
        catch (WorkspaceMemberDeniedException expected) { }
        try { service.grant(id, "alice", "access"); fail("Creator demotion should fail"); }
        catch (WorkspaceMemberDeniedException expected) { }
        verify(site, never()).removeMember("alice");
        verify(site, never()).addMember(eq("alice"), anyString(), anyBoolean(), anyBoolean());
        verify(sites, never()).saveSiteMembership(any(Site.class));
    }

    @Test public void missingCreatorMetadataFailsClosed() throws Exception {
        when(site.getCreatedBy()).thenReturn(null);
        try { service.grant(id, "alice", "access"); fail("Missing owner metadata must block writes"); }
        catch (WorkspaceMemberDeniedException expected) { }
        verify(sites, never()).saveSiteMembership(any(Site.class));
    }

    @Test public void protectedAdminsAreMarkedNonRemovableInMembersPage() {
        Member bob = member("bob", maintain);
        Member otherRoot = member("root2", access);
        Member alice = member("alice", access);
        when(site.getMembers()).thenReturn(Set.of(owner, bob, otherRoot, alice));
        when(security.isSuperUser("root2")).thenReturn(true);
        WorkspaceMemberService.MemberPage page = service.members(id);
        assertFalse(page.members().stream().filter(m -> m.userId().equals("admin")).findFirst().orElseThrow().canRemove());
        assertFalse(page.members().stream().filter(m -> m.userId().equals("root2")).findFirst().orElseThrow().canRemove());
        assertTrue(page.members().stream().filter(m -> m.userId().equals("alice")).findFirst().orElseThrow().canRemove());
    }


    @Test public void ordinaryMaintainerCanStillRemoveAccessMember() throws Exception {
        Member bob = member("bob", maintain);
        Member alice = member("alice", access);
        when(sessions.getCurrentSessionUserId()).thenReturn("bob");
        when(site.getMember("bob")).thenReturn(bob);
        when(site.getMember("alice")).thenReturn(alice);
        when(site.getMembers()).thenReturn(Set.of(owner, bob, alice));
        service.remove(id, "alice");
        verify(site).removeMember("alice");
        verify(sites).saveSiteMembership(site);
    }

}
