/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.List;
import org.junit.Test;
import org.springframework.ui.ConcurrentModel;
import jakarta.servlet.http.HttpServletResponse;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WorkspaceMemberControllerTest {
    @Test public void readLoadsPrivateMemberViewAndCsrf() {
        var svc = mock(WorkspaceMemberService.class);
        var csrf = mock(WorkspaceCsrfService.class);
        var page = new WorkspaceMemberService.MemberPage("bootstrap-7d178d5f-c2c7-41c8-84e7-9ae41955a8e5",
                "Customer Portal", "/portal/site/test", List.of(), "admin", true);
        when(svc.members(page.siteId())).thenReturn(page);
        when(csrf.formTokens()).thenReturn(new WorkspaceCsrfService.FormTokens("t", "n"));
        var model = new ConcurrentModel();
        var response = mock(HttpServletResponse.class);
        assertEquals("workspace_members", new WorkspaceMemberController(svc, csrf).index(page.siteId(), model, response));
        assertEquals(page, model.getAttribute("workspace"));
        assertEquals("t", model.getAttribute("csrfToken"));
        verify(response).setHeader("Cache-Control", "no-store");
    }
    @Test public void writeChecksAuthorizationBeforeConsumingCsrf() {
        var svc = mock(WorkspaceMemberService.class);
        var csrf = mock(WorkspaceCsrfService.class);
        String id = "bootstrap-7d178d5f-c2c7-41c8-84e7-9ae41955a8e5";
        doThrow(new WorkspaceMemberDeniedException()).when(svc).members(id);
        var controller = new WorkspaceMemberController(svc, csrf);
        try { controller.grant(id, "alice", "access", "token", "nonce", new ConcurrentModel(), mock(HttpServletResponse.class));
              fail("Expected forbidden"); }
        catch (WorkspaceMemberDeniedException expected) { }
        verify(csrf, never()).consume(any(), any());
        verify(svc, never()).grant(any(), any(), any());
    }
    @Test public void successfulPostConsumesTokenAndDelegates() {
        var svc = mock(WorkspaceMemberService.class);
        var csrf = mock(WorkspaceCsrfService.class);
        String id = "bootstrap-7d178d5f-c2c7-41c8-84e7-9ae41955a8e5";
        String result = new WorkspaceMemberController(svc, csrf).grant(id, "alice", "access", "token", "nonce", new ConcurrentModel(), mock(HttpServletResponse.class));
        assertEquals("redirect:/members/"+id+"?updated=1", result);
        var order = inOrder(svc, csrf);
        order.verify(svc).members(id);
        order.verify(csrf).consume("token", "nonce");
        order.verify(svc).grant(id, "alice", "access");
    }
}
