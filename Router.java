
package framework;

import java.lang.reflect.*;

import java.util.Map;
import java.util.HashMap;

public class Router 
{
    private final Map<String, Method> routes = new HashMap<>();
    private final Map<String, Object> instances = new HashMap<>();

    public void register(Class<?> classe) throws Exception
    {
        // Créer une instance (un objet) à partir de la classe
        Object instance = classe.getDeclaredConstructor().newInstance();

        // Parcourir les méthodes de la classe
        for(Method fonction : classe.getDeclaredMethods())
        {
            // Si la fonction possède une annotation
            if(fonction.isAnnotationPresent(UrlAnnotation.class))
            {
                UrlAnnotation annotation = fonction.getAnnotation(UrlAnnotation.class);
                String url = annotation.url();

                // Insérer dans la map
                routes.put(url, fonction);
                instances.put(url, instance);
            }
        }
    }

    public String handle(String url) throws Exception
    {
        Method fonction = routes.get(url);
        Object instance = instances.get(url);

        if(fonction != null && instance != null)
        {
            try
            {
                return (String) fonction.invoke(instance);
            }
            catch (Exception e)
            {
                throw new Exception("Erreur lors de l'exécution de la méthode pour l'URL : " + url, e);
            }
        }

        return "404 Not Found";
    }
}
