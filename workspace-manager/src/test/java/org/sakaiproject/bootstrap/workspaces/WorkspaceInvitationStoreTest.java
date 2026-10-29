package org.sakaiproject.bootstrap.workspaces;

import org.junit.Test;
import org.sakaiproject.db.api.SqlService;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies all invitation writes use Sakai's committing SqlService API. */
public class WorkspaceInvitationStoreTest {
    private final SqlService sql = mock(SqlService.class);
    private final WorkspaceInvitationStore store = new WorkspaceInvitationStore(sql);

    private WorkspaceInvitationStore.Invitation sample() {
        return new WorkspaceInvitationStore.Invitation(
                "a01193a0-c25e-41a5-bdb6-9c5714102026", "bootstrap-test", "target",
                "sender", "access", "hash", "PENDING", 100L, 200L);
    }

    @Test public void insertUsesCommittedNativeWriteAndAllNineParameters() throws Exception {
        when(sql.dbWriteCount(anyString(), any(Object[].class), isNull(), isNull(), eq(false)))
                .thenReturn(1);
        store.insert(sample());
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(sql).dbWriteCount(contains("INSERT INTO SAKAI_BOOTSTRAP_INVITATION"),
                values.capture(), isNull(), isNull(), eq(false));
        assertArrayEquals(new Object[] {sample().id(), "bootstrap-test", "target",
                "sender", "access", "hash", "PENDING", 100L, 200L}, values.getValue());
        verify(sql, never()).borrowConnection();
    }

    @Test(expected = IllegalStateException.class)
    public void insertRejectsUncommittedOrUnsuccessfulRowCount() {
        when(sql.dbWriteCount(anyString(), any(Object[].class), isNull(), isNull(), eq(false)))
                .thenReturn(0);
        store.insert(sample());
    }

    @Test public void transitionIsConditionalCommittedWrite() throws Exception {
        when(sql.dbWriteCount(contains("UPDATE SAKAI_BOOTSTRAP_INVITATION"),
                any(Object[].class), isNull(), isNull(), eq(false))).thenReturn(1);
        assertTrue(store.transition(sample().id(), "PENDING", "REVOKED"));
        verify(sql, never()).borrowConnection();
    }

    @Test public void transitionRejectsStaleState() {
        when(sql.dbWriteCount(anyString(), any(Object[].class), isNull(), isNull(), eq(false)))
                .thenReturn(0);
        assertFalse(store.transition(sample().id(), "PENDING", "REVOKED"));
    }

    @Test public void claimRequiresOneActivePendingInvitation() throws Exception {
        when(sql.dbWriteCount(contains("STATUS='CLAIMED'"),
                any(Object[].class), isNull(), isNull(), eq(false))).thenReturn(1,0);
        assertTrue(store.claim(sample().id(), 150L));
        assertFalse(store.claim(sample().id(), 150L));
        verify(sql, never()).borrowConnection();
    }
}
