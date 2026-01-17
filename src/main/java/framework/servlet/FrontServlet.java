package framework.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import framework.annotation.ControllerAnnot;
import framework.annotation.MethodMapping;
import framework.annotation.RequestParam;
import framework.annotation.UrlAnnot;
import framework.annotation.JsonAnnot;
import framework.models.ModelView;
import framework.models.Route;
import framework.util.ProjectConfig;
import framework.util.ProjectScanner;
import framework.util.Convertor;
import framework.util.ObjectMapper;
import framework.util.FrameworkSession;

@WebServlet("/")
@MultipartConfig
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
                            
                            String httpMethod = "GET"; 
                            if (method.isAnnotationPresent(MethodMapping.class)) {
                                httpMethod = method.getAnnotation(MethodMapping.class).value();
                            }

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

        HashMap<String, String> pathVariables = new HashMap<>();
        Route route = findRoute(path, httpMethod, pathVariables);
        PrintWriter writer = rep.getWriter();

        if (route != null) {
            try {
                executeController(route, pathVariables, req, rep);
            } catch (Exception e) {
                e.printStackTrace();
                writer.println("Erreur Serveur: " + e.getMessage());
            }
        } else {
            writer.println("Route introuvable : " + path);
        }
    }

    private Route findRoute(String path, String httpMethod, Map<String, String> pathVariables) {
        for (Route route : routes) {
            if (!route.getMethodHTTP().equalsIgnoreCase(httpMethod)) {
                continue;
            }

            String routeUrl = route.getUrl();
            if (routeUrl.equals(path)) {
                return route;
            }

            pathVariables.clear(); 
            if (isUrlMatch(routeUrl, path, pathVariables)) {
                return route;
            }
        }
        return null;
    }

    private boolean isUrlMatch(String routeUrl, String path, Map<String, String> pathVariables) {
        String regex = "^" + routeUrl.replaceAll("\\{([^/]+)\\}", "([^/]+)") + "$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(path);

        if (matcher.matches()) {
            Pattern namePattern = Pattern.compile("\\{([^/]+)\\}");
            Matcher nameMatcher = namePattern.matcher(routeUrl);
            
            int i = 1;
            while (nameMatcher.find()) {
                String paramName = nameMatcher.group(1);     
                String paramValue = matcher.group(i);        
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
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            String paramName = param.getName();
            Map<String, String[]> rawParameterMap = req.getParameterMap();

            // GESTION DES SESSIONS
            if (param.getType().equals(FrameworkSession.class)) {
                args[i] = new FrameworkSession(req.getSession());
                continue; // On passe au paramètre suivant
            }

            // GESTION DES MAPS (Parameters ou Uploads)
            // Dans ta boucle executeController...

            if (param.getType().equals(Map.class)) {
                
                // 1. Récupération du type générique de la Map (ex: Map<String, ???>)
                ParameterizedType mapType = (ParameterizedType) param.getParameterizedType();
                Type valueType = mapType.getActualTypeArguments()[1]; // On prend le 2ème argument (la Valeur)

                boolean handled = false;

                // -----------------------------------------------------
                // CAS 1 : Upload Simple -> Map<String, byte[]>
                // -----------------------------------------------------
                // On compare directement l'objet Type avec la classe byte[]
                if (valueType instanceof Class && ((Class<?>) valueType) == byte[].class) {
                    
                    Map<String, byte[]> fileMap = new HashMap<>();
                    if (isMultipart(req)) {
                        try {
                            for (Part part : req.getParts()) {
                                if (isPartFile(part)) {
                                    try (InputStream is = part.getInputStream()) {
                                        fileMap.put(part.getName(), is.readAllBytes());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Erreur upload byte[] : " + e.getMessage());
                        }
                    }
                    args[i] = fileMap;
                    handled = true;
                }

                // -----------------------------------------------------
                // CAS 2 : Upload Multiple -> Map<String, List<byte[]>>
                // -----------------------------------------------------
                // On vérifie structurellement si c'est une List<byte[]>
                else if (isListOfByteArray(valueType)) {
                    
                    Map<String, List<byte[]>> multiFileMap = new HashMap<>();
                    if (isMultipart(req)) {
                        try {
                            for (Part part : req.getParts()) {
                                if (isPartFile(part)) {
                                    multiFileMap.putIfAbsent(part.getName(), new ArrayList<>());
                                    try (InputStream is = part.getInputStream()) {
                                        multiFileMap.get(part.getName()).add(is.readAllBytes());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Erreur upload List<byte[]> : " + e.getMessage());
                        }
                    }
                    args[i] = multiFileMap;
                    handled = true;
                }

                // -----------------------------------------------------
                // CAS 3 : Paramètres classiques -> Map<String, Object[]>
                // -----------------------------------------------------
                if (!handled) {
                    // Ta logique existante pour convertir req.getParameterMap()
                    Map<String, Object[]> convertedParameterMap = new HashMap<>();
                    for (Map.Entry<String, String[]> entry : rawParameterMap.entrySet()) {
                        String key = entry.getKey();
                        String[] rawValues = entry.getValue();
                        Object[] convertedValues = new Object[rawValues.length];
                        for (int j = 0; j < rawValues.length; j++) {
                            convertedValues[j] = Convertor.detectAndCastValue(rawValues[j]);
                        }
                        convertedParameterMap.put(key, convertedValues);
                    }
                    args[i] = convertedParameterMap;
                }
                continue;
            }

            // GESTION OBJETS COMPLEXES
            if (!isPrimitiveOrWrapper(param.getType()) && 
                param.getType() != String.class && 
                !param.getType().equals(Map.class)) {
                
                try {
                    Object mappedObject = ObjectMapper.mapToObject(rawParameterMap, param.getType(), paramName);
                    if (mappedObject != null) {
                        args[i] = mappedObject;
                        continue;
                    }
                } catch (Exception e) {
                    System.err.println("Mapping objet échoué: " + e.getMessage());
                }
            }

            // GESTION TYPES SIMPLES (String, int...)
            String value = null;
            if (param.isAnnotationPresent(RequestParam.class)) {
                paramName = param.getAnnotation(RequestParam.class).value();
                value = req.getParameter(paramName);
                if (value == null && pathVariables.containsKey(paramName)) {
                    value = pathVariables.get(paramName);
                }
            } else {
                if (pathVariables.containsKey(paramName)) {
                    value = pathVariables.get(paramName);
                } else {
                    value = req.getParameter(paramName);
                }
            }
            
            args[i] = castValue(value, param.getType());
        }

        // INVOCATION
        Object returnValue = method.invoke(instance, args);

        // GESTION RETOUR (JSON vs VIEW)
        if (method.isAnnotationPresent(JsonAnnot.class)) {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            rep.setContentType("application/json;charset=UTF-8");
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("code", HttpServletResponse.SC_OK);

            if (returnValue instanceof Collection<?>) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("count", ((Collection<?>) returnValue).size());
                dataMap.put("items", returnValue);
                response.put("data", dataMap);
            } else if (returnValue instanceof Map<?, ?>) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("count", ((Map<?, ?>) returnValue).size());
                dataMap.put("items", returnValue);
                response.put("data", dataMap);
            } else {
                response.put("data", returnValue);
            }

            PrintWriter out = rep.getWriter();
            out.print(objectMapper.writeValueAsString(response));
            out.flush();
        } else {
            if (returnValue instanceof ModelView) {
                ModelView mv = (ModelView) returnValue;
                for (Map.Entry<String, Object> entry : mv.getAttributes().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                RequestDispatcher dispatcher = req.getRequestDispatcher(mv.getView());
                dispatcher.forward(req, rep);
            } else if (returnValue instanceof String) {
                rep.getWriter().println((String) returnValue);
            } else {
                rep.getWriter().println(String.valueOf(returnValue));
            }
        }
    }

    private Object castValue(String value, Class<?> type) {
        if (value == null) return null;
        if (type == String.class) return value;
        if (type == Integer.class || type == int.class) return Integer.parseInt(value);
        if (type == Double.class || type == double.class) return Double.parseDouble(value);
        if (type == Float.class || type == float.class) return Float.parseFloat(value);
        if (type == Long.class || type == long.class) return Long.parseLong(value);
        return value; 
    }

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

    /**
     * Vérifie si le type donné correspond exactement à List<byte[]>
     */
    private boolean isListOfByteArray(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            // 1. Est-ce que le conteneur est une List ?
            if (pType.getRawType().equals(List.class)) {
                // 2. Est-ce que le générique interne est byte[] ?
                Type[] args = pType.getActualTypeArguments();
                if (args.length > 0 && args[0] == byte[].class) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Vérifie si la requête est multipart
     */
    private boolean isMultipart(HttpServletRequest req) {
        String contentType = req.getContentType();
        return contentType != null && contentType.startsWith("multipart/form-data");
    }

    /**
     * Vérifie si une "Part" est bien un fichier (et non un champ texte)
     */
    private boolean isPartFile(Part part) {
        return part.getSubmittedFileName() != null && !part.getSubmittedFileName().isEmpty();
    }
}