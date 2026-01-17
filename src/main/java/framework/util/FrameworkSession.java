package framework.util;

import javax.servlet.http.HttpSession;

public class FrameworkSession {
    
    private HttpSession session;

    public FrameworkSession(HttpSession session) {
        this.session = session;
    }

    // Récupérer une valeur
    public Object get(String key) {
        return this.session.getAttribute(key);
    }

    // Ajouter une valeur
    public void put(String key, Object value) {
        this.session.setAttribute(key, value);
    }

    // Supprimer une valeur
    public void remove(String key) {
        this.session.removeAttribute(key);
    }

    // Invalider (Logout)
    public void invalidate() {
        this.session.invalidate();
    }
}