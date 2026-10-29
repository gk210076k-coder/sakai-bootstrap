/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import jakarta.annotation.Resource;
import org.sakaiproject.db.api.SqlService;
import org.springframework.stereotype.Repository;

/** Durable MySQL-backed invitations, isolated from Sakai's native realm tables. */
@Repository
public class WorkspaceInvitationStore {
    public record Invitation(String id, String siteId, String invitee, String inviter,
                             String role, String tokenHash, String status,
                             long createdAt, long expiresAt) {
        public boolean expired(long nowSeconds) { return nowSeconds >= expiresAt; }
    }
    @Resource private SqlService sql;
    public WorkspaceInvitationStore() { }
    WorkspaceInvitationStore(SqlService sql) { this.sql = sql; }

    private interface ConnectionAction<T> { T run(Connection connection) throws SQLException; }
    private <T> T jdbc(ConnectionAction<T> action) {
        Connection c = null;
        try {
            c = sql.borrowConnection();
            return action.run(c);
        } catch (SQLException ex) {
            throw new IllegalStateException("WM04 invitation database unavailable or migration missing", ex);
        } finally {
            if (c != null) sql.returnConnection(c);
        }
    }

    /**
     * Use Sakai's committed write primitive. Its default JDBC pool may supply
     * connections with autoCommit=false; a bare executeUpdate() followed by
     * returnConnection() can otherwise silently roll back on close.
     *
     * dbWriteCount() commits standalone writes and respects an existing
     * transaction, matching Sakai's native SqlService contract.
     */
    public void insert(Invitation item) {
        int count = sql.dbWriteCount("""
                INSERT INTO SAKAI_BOOTSTRAP_INVITATION
                (INVITATION_ID, SITE_ID, INVITEE_USER_ID, INVITED_BY, ROLE_ID,
                 TOKEN_HASH, STATUS, CREATED_AT, EXPIRES_AT)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                new Object[] { item.id(), item.siteId(), item.invitee(), item.inviter(),
                        item.role(), item.tokenHash(), item.status(), item.createdAt(),
                        item.expiresAt() },
                null, null, false);
        if (count != 1) throw new IllegalStateException("WM04 invitation insert was not committed");
    }

    public List<Invitation> list(String siteId) {
        return jdbc(c -> {
            List<Invitation> results = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("""
                    SELECT INVITATION_ID,SITE_ID,INVITEE_USER_ID,INVITED_BY,ROLE_ID,
                           TOKEN_HASH,STATUS,CREATED_AT,EXPIRES_AT
                    FROM SAKAI_BOOTSTRAP_INVITATION WHERE SITE_ID = ?
                    ORDER BY CREATED_AT DESC, INVITATION_ID DESC LIMIT 100
                    """)) {
                p.setString(1, siteId);
                try (ResultSet rows = p.executeQuery()) {
                    while (rows.next()) results.add(read(rows));
                }
            }
            return List.copyOf(results);
        });
    }

    /** Targeted query; checking only the latest 100 history rows would miss older pending invitations. */
    public boolean hasPending(String siteId, String inviteeId, long now) {
        return jdbc(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    SELECT INVITATION_ID FROM SAKAI_BOOTSTRAP_INVITATION
                    WHERE SITE_ID=? AND INVITEE_USER_ID=?
                      AND STATUS='PENDING' AND EXPIRES_AT>?
                    LIMIT 1
                    """)) {
                p.setString(1, siteId); p.setString(2, inviteeId); p.setLong(3, now);
                try (ResultSet rows = p.executeQuery()) { return rows.next(); }
            }
        });
    }

    public Invitation findByHash(String tokenHash) {
        return jdbc(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    SELECT INVITATION_ID,SITE_ID,INVITEE_USER_ID,INVITED_BY,ROLE_ID,
                           TOKEN_HASH,STATUS,CREATED_AT,EXPIRES_AT
                    FROM SAKAI_BOOTSTRAP_INVITATION WHERE TOKEN_HASH = ?
                    """)) {
                p.setString(1, tokenHash);
                try (ResultSet rows = p.executeQuery()) {
                    return rows.next() ? read(rows) : null;
                }
            }
        });
    }

    public Invitation findBySiteAndId(String siteId, String invitationId) {
        return jdbc(c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    SELECT INVITATION_ID,SITE_ID,INVITEE_USER_ID,INVITED_BY,ROLE_ID,
                           TOKEN_HASH,STATUS,CREATED_AT,EXPIRES_AT
                    FROM SAKAI_BOOTSTRAP_INVITATION WHERE SITE_ID = ? AND INVITATION_ID = ?
                    """)) {
                p.setString(1, siteId); p.setString(2, invitationId);
                try (ResultSet rows = p.executeQuery()) {
                    return rows.next() ? read(rows) : null;
                }
            }
        });
    }

    /** Compare-and-swap prevents double redemption and late revocation. */
    public boolean transition(String id, String from, String to) {
        return sql.dbWriteCount("""
                UPDATE SAKAI_BOOTSTRAP_INVITATION SET STATUS=?
                WHERE INVITATION_ID=? AND STATUS=?
                """, new Object[] { to, id, from }, null, null, false) == 1;
    }

    public boolean claim(String id, long now) {
        return sql.dbWriteCount("""
                UPDATE SAKAI_BOOTSTRAP_INVITATION SET STATUS='CLAIMED'
                WHERE INVITATION_ID=? AND STATUS='PENDING' AND EXPIRES_AT>?
                """, new Object[] { id, now }, null, null, false) == 1;
    }

    private static Invitation read(ResultSet row) throws SQLException {
        return new Invitation(row.getString(1), row.getString(2), row.getString(3),
                row.getString(4), row.getString(5), row.getString(6),
                row.getString(7), row.getLong(8), row.getLong(9));
    }
}
