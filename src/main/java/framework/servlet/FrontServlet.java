package framework.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Parameter;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 
    Import custom
*/
import framework.annotation.ControllerAnnot;
import framework.annotation.UrlAnnot;

import framework.util.ProjectConfig;
import framework.util.ProjectScanner;

import framework.models.ModelView;

@WebServlet("/")
public class FrontServlet extends HttpServlet
{
    private Map<String, Class<?>> routes = new HashMap<>();

    @Override
    public void init() throws ServletException
    {
        super.init();
        System.out.println("Test Init FrontServlet");

        ProjectConfig config = new ProjectConfig();

        String basePackage = config.getProperty("PACKAGE_RACINE");

        System.out.println("Base package: " + basePackage);
        
        ProjectScanner scanner = new ProjectScanner(basePackage);
        Set<Class<?>> projectClasses = scanner.getAllProjectClasses();
        List<Class<?>> listClasses = new ArrayList<>(projectClasses);

        for (Class<?> clazz : listClasses)
        {
            if (clazz.isAnnotationPresent(ControllerAnnot.class))
            {
                // Récupère les fonctions publiques
                Method[] methods = clazz.getMethods();
                for (Method method : methods)
                {
                    if (method.isAnnotationPresent(UrlAnnot.class))
                    {
                        UrlAnnot urlAnnot = method.getAnnotation(UrlAnnot.class);
                        String url = urlAnnot.value();
                        routes.put(url, clazz);
                    }
                }
            }
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rep) throws ServletException, IOException
    {
        try {
            customRedirect(req, rep);
        } catch (ReflectiveOperationException e) {
            throw new ServletException("Error invoking controller method", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse rep) throws ServletException, IOException
    {
        try {
            customRedirect(req, rep);
        } catch (ReflectiveOperationException e) {
            throw new ServletException("Error invoking controller method", e);
        }
    }

    private void customRedirect(HttpServletRequest req, HttpServletResponse rep) throws IOException, ServletException, ReflectiveOperationException
    {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        boolean pathExists = getServletContext().getResource(path) != null;

        if (routes.containsKey(path))
        {
            Class<?> clazz = routes.get(path);

            afficher(clazz, path, req, rep);
        }
        // Lien Dynamique (qui possède {})
        else
        {
            for (Map.Entry<String, Class<?>> entry : routes.entrySet()) 
            {
                String route = entry.getKey();
                Class<?> clazz = entry.getValue();

                if (equalToDynamicLink(route, path))
                {
                    afficher(clazz, route, req, rep);
                    return;
                }
            }

            PrintWriter writer = rep.getWriter();
            writer.println("Chemin introuvable : ");
            writer.println(path);
            rep.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

    }

    void afficher(Class<?> clazz, String path, HttpServletRequest req, HttpServletResponse rep) throws IOException, ServletException, ReflectiveOperationException
    {
        Method[] methods = clazz.getMethods();
        for (Method method : methods)
        {
            if (method.isAnnotationPresent(UrlAnnot.class))
            {
                UrlAnnot url = method.getAnnotation(UrlAnnot.class);

                if (url.value().equals(path))
                {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    Parameter[] params = method.getParameters();
                    Object[] args = new Object[params.length];

                    // Mapper les paramètres de la méthode avec les données de la requête
                    for (int i = 0; i < params.length; i++)
                    {
                        String paramName = params[i].getName(); // Nom du paramètre
                        String paramValue = req.getParameter(paramName); // Valeur venant du formulaire
                        args[i] = paramValue; // Assigner la valeur (null si absente)
                    }

                    // Invoquer la méthode avec les arguments mappés
                    Object resultat = method.invoke(instance, args);

                    // Gérer le type de retour
                    switch (method.getReturnType().getName())
                    {
                        case "java.lang.String":
                            {
                                PrintWriter writer = rep.getWriter();
                                String vue = (String) resultat;
                                writer.println(vue);
                                break;
                            }

                        case "framework.models.ModelView":
                            {
                                ModelView mv = (ModelView) resultat;
                                String vue = mv.getView();

                                HashMap<String, Object> atts = mv.getAttributes();
                                for (Map.Entry<String, Object> entry : atts.entrySet())
                                {
                                    req.setAttribute(entry.getKey(), entry.getValue());
                                }

                                RequestDispatcher dispatcher = req.getRequestDispatcher(vue);
                                dispatcher.forward(req, rep);
                                break;
                            }


                        default:
                            {
                                PrintWriter writer = rep.getWriter();
                                writer.println("Type de retour non géré : " + method.getReturnType().getName());
                                break;
                            }
                    }
                }
            }
        }
    }

    public boolean equalToDynamicLink(String route, String path)
    {
        String regex = route.replaceAll("\\{[^/]+\\}", "[^/]+");
        return path.matches(regex);
    }
}
