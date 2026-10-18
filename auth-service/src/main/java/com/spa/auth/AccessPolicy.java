package com.spa.auth;

import java.util.Set;

public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static boolean allows(String role, String method, String path) {
        if (!Set.of("OWNER", "ADMIN", "STAFF").contains(role)) return false;
        if (path.equals("/_monitoring")) return !role.equals("STAFF");
        if (
                path.startsWith("/api/website/") || path.equals("/api/statistics")
        ) return !role.equals("STAFF");
        boolean known = path.matches(
                "/api/(appointments(?:/[a-fA-F0-9-]+)?|notifications|staff(?:/[a-fA-F0-9-]+)?|attendance|sales|operations/settings|contracts(?:/[a-fA-F0-9-]+/(?:pdf|pack[.]pdf))?|salaries(?:/[a-fA-F0-9-]+/pdf)?|expenses|contacts(?:/[a-fA-F0-9-]+)?|reports/(?:sales[.]csv|profit-loss(?:[.]pdf)?))"
        );
        if (!known) return false;
        if (!role.equals("STAFF")) return true;
        boolean read = method.equals("GET") || method.equals("HEAD");
        if (
                path.equals("/api/staff") ||
                        path.equals("/api/contacts") ||
                        path.equals("/api/notifications") ||
                        path.equals("/api/operations/settings")
        ) return read;
        return (
                (path.matches("/api/appointments(?:/[a-fA-F0-9-]+)?") ||
                        path.equals("/api/sales")) &&
                        (read || method.equals("POST"))
        );
    }
}
