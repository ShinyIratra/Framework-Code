package framework.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import framework.annotation.ControllerAnnot;
import framework.annotation.UrlAnnot;

import framework.util.ProjectConfig;
import framework.util.ProjectScanner;

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
        customRedirect(req, rep);
    }

    private void customRedirect(HttpServletRequest req, HttpServletResponse rep) throws IOException, ServletException
    {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        boolean pathExists = getServletContext().getResource(path) != null;

        PrintWriter writer = rep.getWriter();

        if (routes.containsKey(path))
        {
            Class<?> clazz = routes.get(path);
            String packageName = clazz.getPackageName();
            writer.println(packageName + "." + clazz.getSimpleName() + " : " + path);
        }
        // Introuvable
        else
        {
            writer.println("Chemin introuvable : ");
            writer.println(path);
            rep.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        
    }
}
