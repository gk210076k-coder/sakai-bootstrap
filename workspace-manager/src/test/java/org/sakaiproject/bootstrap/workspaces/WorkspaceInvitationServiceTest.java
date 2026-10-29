package org.sakaiproject.bootstrap.workspaces;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.springframework.test.util.ReflectionTestUtils;
import org.sakaiproject.entity.api.ResourceProperties;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import org.mockito.ArgumentCaptor;

public class WorkspaceInvitationServiceTest {
    private static final String ID = "bootstrap-63280572-e1e6-44b9-9aff-349745ebe472";
    private static final long NOW = 1793270000L;
    private final WorkspaceInvitationStore store = mock(WorkspaceInvitationStore.class);
    private final WorkspaceMemberService members = mock(WorkspaceMemberService.class);
    private final SiteService sites = mock(SiteService.class);
    private final SessionManager session = mock(SessionManager.class);
    private final UserDirectoryService users = mock(UserDirectoryService.class);
    private final SecurityService security = mock(SecurityService.class);
    private final Site site = mock(Site.class);
    private final Role role = mock(Role.class);
    private final User original = mock(User.class);
    private final ResourceProperties props = mock(ResourceProperties.class);
    private WorkspaceInvitationService svc;

    @Before public void init() throws Exception {
        svc = new WorkspaceInvitationService(Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        ReflectionTestUtils.setField(svc, "store", store);
        ReflectionTestUtils.setField(svc, "members", members);
        ReflectionTestUtils.setField(svc, "sites", sites);
        ReflectionTestUtils.setField(svc, "sessions", session);
        ReflectionTestUtils.setField(svc, "users", users);
        ReflectionTestUtils.setField(svc, "security", security);
        when(sites.getSite(ID)).thenReturn(site);
        when(site.getId()).thenReturn(ID);
        when(site.getReference()).thenReturn("/site/" + ID);
        when(site.isPublished()).thenReturn(true);
        when(site.getType()).thenReturn("project");
        when(site.getProperties()).thenReturn(props);
        when(props.getProperty(WorkspaceCreationService.WORKSPACE_TYPE_PROPERTY)).thenReturn("application");
        when(props.getProperty(WorkspaceCreationService.WORKSPACE_VISIBILITY_PROPERTY)).thenReturn("private");
        when(site.getRole("access")).thenReturn(role);
        when(role.isAllowed(SiteService.SITE_VISIT)).thenReturn(true);
        when(site.getCreatedBy()).thenReturn(original);
        when(original.getId()).thenReturn("creator");
        when(session.getCurrentSessionUserId()).thenReturn("maintainer");
    }

    @Test public void issuingPersistsOnlyTokenHash() {
        when(store.hasPending(eq(ID), eq("invitee"), anyLong())).thenReturn(false);
        var result = svc.issue(ID, "invitee", "access");
        ArgumentCaptor<WorkspaceInvitationStore.Invitation> arg =
                ArgumentCaptor.forClass(WorkspaceInvitationStore.Invitation.class);
        verify(store).insert(arg.capture());
        var invitation = arg.getValue();
        assertEquals("PENDING", invitation.status());
        assertEquals("invitee", invitation.invitee());
        assertEquals("maintainer", invitation.inviter());
        assertEquals("access", invitation.role());
        assertEquals(NOW + 7*24*60*60L, invitation.expiresAt());
        assertEquals(WorkspaceInvitationToken.hash(result.secret()), invitation.tokenHash());
        assertNotEquals(result.secret(), invitation.tokenHash());
    }

    @Test(expected = WorkspaceInvitationInvalidException.class)
    public void duplicatePendingInvitationsAreRejected() {
        when(store.hasPending(ID, "invitee", NOW)).thenReturn(true);
        svc.issue(ID, "invitee", "access");
    }

    @Test(expected = WorkspaceInvitationDeniedException.class)
    public void superusersCannotBeInvited() {
        when(security.isSuperUser("postmaster")).thenReturn(true);
        svc.issue(ID, "postmaster", "access");
    }

    @Test public void accessIsCheckedBeforeListing() {
        doThrow(new WorkspaceMemberDeniedException()).when(members).members(ID);
        try { svc.manage(ID); fail("must deny"); }
        catch (WorkspaceMemberDeniedException expected) { }
        verify(store, never()).list(anyString());
    }

    @Test(expected = WorkspaceInvitationDeniedException.class)
    public void bearerTokenAloneDoesNotAllowAnotherAccountToAccept() {
        String raw = WorkspaceInvitationToken.generate();
        when(store.findByHash(WorkspaceInvitationToken.hash(raw))).thenReturn(invitation(raw));
        when(session.getCurrentSessionUserId()).thenReturn("other-user");
        svc.accept(raw);
    }

    @Test public void membershipCannotBeOverwrittenByAccept() {
        String raw = WorkspaceInvitationToken.generate();
        when(store.findByHash(WorkspaceInvitationToken.hash(raw))).thenReturn(invitation(raw));
        when(session.getCurrentSessionUserId()).thenReturn("invitee");
        when(site.getMember("invitee")).thenReturn(mock(org.sakaiproject.authz.api.Member.class));
        try { svc.accept(raw); fail("must deny"); }
        catch (WorkspaceInvitationInvalidException expected) { }
        verify(store, never()).claim(anyString(), anyLong());
        verify(site, never()).addMember(anyString(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test public void acceptingAddsOnlyTargetWithSiteScopedMembershipAdvice() throws Exception {
        String raw = WorkspaceInvitationToken.generate();
        var inv = invitation(raw);
        when(store.findByHash(inv.tokenHash())).thenReturn(inv);
        when(session.getCurrentSessionUserId()).thenReturn("invitee");
        when(store.claim(inv.id(), NOW)).thenReturn(true);
        when(store.transition(inv.id(), "CLAIMED", "ACCEPTED")).thenReturn(true);
        assertEquals(ID, svc.accept(raw));
        verify(site).addMember("invitee", "access", true, false);
        verify(sites).saveSiteMembership(site);
        ArgumentCaptor<SecurityAdvisor> arg = ArgumentCaptor.forClass(SecurityAdvisor.class);
        verify(security).pushAdvisor(arg.capture());
        verify(security).popAdvisor(arg.getValue());
        var advisor = arg.getValue();
        assertEquals(SecurityAdvisor.SecurityAdvice.ALLOWED,
                advisor.isAllowed("invitee", SiteService.SECURE_UPDATE_SITE_MEMBERSHIP, "/site/"+ID));
        assertEquals(SecurityAdvisor.SecurityAdvice.PASS,
                advisor.isAllowed("other", SiteService.SECURE_UPDATE_SITE_MEMBERSHIP, "/site/"+ID));
        assertEquals(SecurityAdvisor.SecurityAdvice.PASS,
                advisor.isAllowed("invitee", SiteService.SECURE_UPDATE_SITE_MEMBERSHIP, "/site/other"));
        assertEquals(SecurityAdvisor.SecurityAdvice.PASS,
                advisor.isAllowed("invitee", SiteService.SECURE_UPDATE_SITE, "/site/"+ID));
    }

    @Test public void failedMembershipWriteReleasesClaimAndAdvisor() throws Exception {
        String raw = WorkspaceInvitationToken.generate();
        var inv = invitation(raw);
        when(store.findByHash(inv.tokenHash())).thenReturn(inv);
        when(session.getCurrentSessionUserId()).thenReturn("invitee");
        when(store.claim(inv.id(), NOW)).thenReturn(true);
        doThrow(new org.sakaiproject.exception.PermissionException("invitee", "site.upd.site.mbrshp", ID))
                .when(sites).saveSiteMembership(site);
        try { svc.accept(raw); fail("must deny"); }
        catch (WorkspaceInvitationDeniedException expected) { }
        verify(store).transition(inv.id(), "CLAIMED", "PENDING");
        verify(security).popAdvisor(any(SecurityAdvisor.class));
    }

    private WorkspaceInvitationStore.Invitation invitation(String raw) {
        return new WorkspaceInvitationStore.Invitation("c502bd18-97c8-4f19-9843-cd9ecccb2926",
                ID, "invitee", "maintainer", "access", WorkspaceInvitationToken.hash(raw),
                "PENDING", NOW-50, NOW+3600);
    }
}
