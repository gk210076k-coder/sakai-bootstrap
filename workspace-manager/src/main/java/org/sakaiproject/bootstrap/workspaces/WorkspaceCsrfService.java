/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import jakarta.annotation.Resource;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.springframework.stereotype.Service;

/** Session-owned synchronizer token + single-use form nonce, no cookies/JS secrets. */
@Service
public class WorkspaceCsrfService {
    static final String TOKEN_KEY = "bootstrap.workspace.csrf";
    static final String NONCE_KEY = "bootstrap.workspace.nonce";
    static final String USER_KEY = "bootstrap.workspace.csrf.user";
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private SessionManager sessions;

    public WorkspaceCsrfService() { }
    WorkspaceCsrfService(SessionManager sessions) { this.sessions = sessions; }

    public FormTokens formTokens() {
        Session session = authenticatedSession();
        synchronized (session) {
            String userId = session.getUserId();
            if (!userId.equals(session.getAttribute(USER_KEY))) {
                session.setAttribute(USER_KEY, userId);
                session.setAttribute(TOKEN_KEY, randomValue());
                session.setAttribute(NONCE_KEY, randomValue());
            }
            if (!(session.getAttribute(TOKEN_KEY) instanceof String)) session.setAttribute(TOKEN_KEY, randomValue());
            if (!(session.getAttribute(NONCE_KEY) instanceof String)) session.setAttribute(NONCE_KEY, randomValue());
            return new FormTokens((String) session.getAttribute(TOKEN_KEY),
                    (String) session.getAttribute(NONCE_KEY));
        }
    }

    public void consume(String token, String nonce) {
        Session session = authenticatedSession();
        synchronized (session) {
            if (!session.getUserId().equals(session.getAttribute(USER_KEY))
                    || !constantEqual(token, session.getAttribute(TOKEN_KEY))
                    || !constantEqual(nonce, session.getAttribute(NONCE_KEY))) {
                throw new WorkspaceCreationDeniedException();
            }
            // Prevent duplicate submits within one session, including concurrently.
            session.removeAttribute(NONCE_KEY);
        }
    }

    private Session authenticatedSession() {
        Session session = sessions.getCurrentSession();
        if (session == null || session.getUserId() == null || session.getUserId().isBlank()) {
            throw new WorkspaceUnauthenticatedException();
        }
        return session;
    }

    private static boolean constantEqual(String provided, Object stored) {
        if (provided == null || !(stored instanceof String valid)) return false;
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), valid.getBytes(StandardCharsets.UTF_8));
    }
    private static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public record FormTokens(String csrfToken, String nonce) { }
}
