package org.sakaiproject.bootstrap.workspaces;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkspaceInvitationTokenTest {
    @Test public void generatesUniqueUrlSafeSecretsAndStoresOnlyHashes() {
        String a = WorkspaceInvitationToken.generate();
        String b = WorkspaceInvitationToken.generate();
        assertEquals(43, a.length());
        assertEquals(43, b.length());
        assertTrue(a.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(a, b);
        assertEquals(64, WorkspaceInvitationToken.hash(a).length());
        assertNotEquals(a, WorkspaceInvitationToken.hash(a));
        assertNotEquals(WorkspaceInvitationToken.hash(a), WorkspaceInvitationToken.hash(b));
        assertEquals(WorkspaceInvitationToken.hash(a), WorkspaceInvitationToken.hash(a));
    }
    @Test(expected = WorkspaceInvitationInvalidException.class)
    public void rejectsMalformedToken() { WorkspaceInvitationToken.hash("too-short"); }
    @Test(expected = WorkspaceInvitationInvalidException.class)
    public void rejectsNullToken() { WorkspaceInvitationToken.hash(null); }
}
