package framework.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import framework.annotation.ControllerAnnot;
import framework.annotation.MethodMapping;
import framework.annotation.RequestParam;
import framework.annotation.UrlAnnot;
import framework.models.ModelView;
import framework.models.Route;
import framework.util.ProjectConfig;
import framework.util.ProjectScanner;
import framework.util.Convertor;
import framework.util.ObjectMapper;

@WebServlet("/")
public class FrontServlet extends HttpServlet {
    
    private List<Route> routes = new ArrayList<>();

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            ProjectConfig config = new ProjectConfig();
            String basePackage = config.getProperty("PACKAGE_RACINE");
            
            ProjectScanner scanner = new ProjectScanner(basePackage);
            Set<Class<?>> projectClasses = scanner.getAllProjectClasses();

            for (Class<?> clazz : projectClasses) {
                if (clazz.isAnnotationPresent(ControllerAnnot.class)) {
                    for (Method method : clazz.getMethods()) {
                        if (method.isAnnotationPresent(UrlAnnot.class)) {
                            UrlAnnot urlAnnot = method.getAnnotation(UrlAnnot.class);
                            String url = urlAnnot.value();
                            
                            String httpMethod = "GET"; // Valeur par défaut
                            if (method.isAnnotationPresent(MethodMapping.class)) {
                                httpMethod = method.getAnnotation(MethodMapping.class).value();
                            }

                            // On ajoute la route à la liste (avec l'URL dans l'objet Route)
                            Route route = new Route(clazz, method, httpMethod, url);
                            routes.add(route);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new ServletException("Erreur Init FrontServlet", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rep) throws ServletException, IOException {
        processRequest(req, rep);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse rep) throws ServletException, IOException {
        processRequest(req, rep);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse rep) throws IOException, ServletException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String httpMethod = req.getMethod();

        // Map qui contiendra les variables d'URL si trouvées (ex: id -> 5)
        HashMap<String, String> pathVariables = new HashMap<>();
        
        // Recherche de la route
        Route route = findRoute(path, httpMethod, pathVariables);

        if (route != null) {
            try {
                executeController(route, pathVariables, req, rep);
            } catch (Exception e) {
                e.printStackTrace();
                rep.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } else {

            PrintWriter writer = rep.getWriter();
            writer.println("URL Introuvable : " + path);
            // rep.sendError(HttpServletResponse.SC_NOT_FOUND, "Route introuvable : " + path);
        }
    }

    /**
     * Parcourt la liste des routes pour trouver celle qui correspond à l'URL et au verbe HTTP.
     * Remplie pathVariables si c'est une URL dynamique.
     */
    private Route findRoute(String path, String httpMethod, Map<String, String> pathVariables) {
        for (Route route : routes) {
            // 1. Vérif Verbe HTTP (GET, POST...)
            if (!route.getMethodHTTP().equalsIgnoreCase(httpMethod)) {
                continue;
            }

            String routeUrl = route.getUrl();

            // 2. Vérif Correspondance Exacte
            if (routeUrl.equals(path)) {
                return route;
            }

            // 3. Vérif Regex (Dynamique)
            // On vide la map temporaire avant de tester
            pathVariables.clear(); 
            if (isUrlMatch(routeUrl, path, pathVariables)) {
                return route;
            }
        }
        return null;
    }

    private boolean isUrlMatch(String routeUrl, String path, Map<String, String> pathVariables) {
        // Transforme /user/{id} en /user/([^/]+)
        String regex = "^" + routeUrl.replaceAll("\\{([^/]+)\\}", "([^/]+)") + "$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(path);

        if (matcher.matches()) {
            // Récupère les noms des paramètres dans l'URL de la route
            Pattern namePattern = Pattern.compile("\\{([^/]+)\\}");
            Matcher nameMatcher = namePattern.matcher(routeUrl);
            
            int i = 1;
            while (nameMatcher.find()) {
                String paramName = nameMatcher.group(1);     // ex: "id"
                String paramValue = matcher.group(i);        // ex: "12"
                pathVariables.put(paramName, paramValue);
                i++;
            }
            return true;
        }
        return false;
    }

    private void executeController(Route route, Map<String, String> pathVariables, HttpServletRequest req, HttpServletResponse rep) throws Exception {
        Method method = route.getMethod();
        Object instance = route.getClazz().getDeclaredConstructor().newInstance();

        // Récupération des arguments
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            String paramName = param.getName();
            String value = null;
            Map<String, String[]> rawParameterMap = req.getParameterMap();

            // Si Map, on prend les arguments directement
            if (param.getType().equals(Map.class)) 
            {
                Map<String, Object[]> convertedParameterMap = new HashMap<>();

                for (Map.Entry<String, String[]> entry : rawParameterMap.entrySet()) {
                    String key = entry.getKey();
                    String[] rawValues = entry.getValue();

                    // Convertir chaque valeur dans le tableau
                    Object[] convertedValues = new Object[rawValues.length];
                    for (int j = 0; j < rawValues.length; j++) {
                        convertedValues[j] = Convertor.detectAndCastValue(rawValues[j]);
                    }

                    convertedParameterMap.put(key, convertedValues);
                }

                args[i] = convertedParameterMap;
                continue;
            }

            // Vérifier si c'est un objet complexe (pas primitif, pas String, pas Map)
            if (!isPrimitiveOrWrapper(param.getType()) && 
                param.getType() != String.class && 
                !param.getType().equals(Map.class)) {
                
                // Tenter le mapping automatique
                try {
                    Object mappedObject = ObjectMapper.mapToObject(rawParameterMap, param.getType(), paramName);
                    if (mappedObject != null) {
                        args[i] = mappedObject;
                        continue;
                    }
                } catch (Exception e) {
                    System.err.println("Erreur lors du mapping de l'objet " + paramName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Priorité 1: Annotation @RequestParam
            if (param.isAnnotationPresent(RequestParam.class)) {
                paramName = param.getAnnotation(RequestParam.class).value();
                // On cherche d'abord dans la requête classique (?id=...)
                value = req.getParameter(paramName);
                // Si null, on d' regarde dans les variablesURL (/user/{id})
                if (value == null && pathVariables.containsKey(paramName)) {
                    value = pathVariables.get(paramName);
                }
            } 
            // Priorité 2: Nom du paramètre
            else {
                // Cherche dans URL variables d'abord, puis query params
                if (pathVariables.containsKey(paramName)) {
                    value = pathVariables.get(paramName);
                } else {
                    value = req.getParameter(paramName);
                }
            }
            
            args[i] = castValue(value, param.getType());
        }

        // Invocation
        Object returnValue = method.invoke(instance, args);

        // Gestion du retour
        if (returnValue instanceof ModelView) {
            ModelView mv = (ModelView) returnValue;
            for (Map.Entry<String, Object> entry : mv.getAttributes().entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
            RequestDispatcher dispatcher = req.getRequestDispatcher(mv.getView());
            dispatcher.forward(req, rep);
        } 
        else if (returnValue instanceof String) {
            PrintWriter out = rep.getWriter();
            out.println((String) returnValue);
        }
        else {
            // Gestion des types primitifs (int, float, etc.)
            PrintWriter out = rep.getWriter();
            out.println(String.valueOf(returnValue));
        }
    }

    // Convertisseur simple String -> Type cible
    private Object castValue(String value, Class<?> type) {
        if (value == null) return null; // Ou valeur par défaut selon besoin

        if (type == String.class) return value;
        if (type == Integer.class || type == int.class) return Integer.parseInt(value);
        if (type == Double.class || type == double.class) return Double.parseDouble(value);
        if (type == Float.class || type == float.class) return Float.parseFloat(value);
        if (type == Long.class || type == long.class) return Long.parseLong(value);
        
        return value; 
    }

    // Vérifie si un type est primitif ou wrapper
    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Boolean.class ||
               type == Character.class ||
               type == Byte.class ||
               type == Short.class;
    }
}