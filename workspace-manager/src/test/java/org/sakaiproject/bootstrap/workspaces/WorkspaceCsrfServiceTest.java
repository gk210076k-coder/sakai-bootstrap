/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.Map;
import java.util.HashMap;
import org.junit.Test;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WorkspaceCsrfServiceTest {
    @Test public void tokenIsSingleUseAndBoundToSakaiUser() {
        SessionManager manager = mock(SessionManager.class);
        Session session = mock(Session.class);
        Map<String, Object> values = new HashMap<>();
        when(manager.getCurrentSession()).thenReturn(session);
        when(session.getUserId()).thenReturn("owner");
        when(session.getAttribute(anyString())).thenAnswer(i -> values.get(i.getArgument(0)));
        doAnswer(i -> { values.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
        doAnswer(i -> { values.remove(i.getArgument(0)); return null; })
                .when(session).removeAttribute(anyString());
        WorkspaceCsrfService csrf = new WorkspaceCsrfService(manager);
        WorkspaceCsrfService.FormTokens tokens = csrf.formTokens();
        assertNotNull(tokens.csrfToken());
        assertNotNull(tokens.nonce());
        csrf.consume(tokens.csrfToken(), tokens.nonce());
        try { csrf.consume(tokens.csrfToken(), tokens.nonce()); fail("replay should be denied"); }
        catch (WorkspaceCreationDeniedException expected) { }
        when(session.getUserId()).thenReturn("different-user");
        try { csrf.consume(tokens.csrfToken(), tokens.nonce()); fail("new user should be denied"); }
        catch (WorkspaceCreationDeniedException expected) { }
    }
    @Test(expected = WorkspaceUnauthenticatedException.class)
    public void anonymousSessionCannotRequestTokens() {
        SessionManager manager = mock(SessionManager.class);
        when(manager.getCurrentSession()).thenReturn(null);
        new WorkspaceCsrfService(manager).formTokens();
    }
}
