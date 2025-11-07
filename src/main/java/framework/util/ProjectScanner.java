package framework.util;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import java.util.Set;

public class ProjectScanner {
    private final Reflections reflections;

    public ProjectScanner(String basePackage) {
        this.reflections = new Reflections(basePackage, new SubTypesScanner(false));
    }

    /**
     * Retourne toutes les classes du projet dans le package donné
     */
    public Set<Class<?>> getAllProjectClasses() {
        return reflections.getSubTypesOf(Object.class);
    }
}
