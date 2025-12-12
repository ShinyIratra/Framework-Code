package framework.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectMapper {
    
    /**
     * Mappe les paramètres de la requête vers un objet du type spécifié
     * @param parameterMap Map des paramètres de la requête
     * @param targetClass La classe de l'objet à créer
     * @param paramPrefix Le préfixe du paramètre (ex: "e" pour "e.name")
     * @return L'objet mappé ou null si aucun paramètre correspondant
     */
    public static Object mapToObject(Map<String, String[]> parameterMap, Class<?> targetClass, String paramPrefix) throws Exception {
        // Vérifier si des paramètres commencent par le préfixe
        boolean hasMatchingParams = false;
        for (String key : parameterMap.keySet()) {
            if (key.startsWith(paramPrefix + ".")) {
                hasMatchingParams = true;
                break;
            }
        }
        
        if (!hasMatchingParams) {
            return null;
        }
        
        // Créer une instance de l'objet
        Object instance = targetClass.getDeclaredConstructor().newInstance();
        
        // Grouper les paramètres par attribut
        Map<String, Map<String, String>> groupedParams = groupParametersByAttribute(parameterMap, paramPrefix);
        
        // Remplir les champs de l'objet
        for (Map.Entry<String, Map<String, String>> entry : groupedParams.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, String> fieldParams = entry.getValue();
            
            try {
                Field field = findField(targetClass, fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    Object fieldValue = mapFieldValue(field, fieldParams, paramPrefix + "." + fieldName);
                    field.set(instance, fieldValue);
                }
            } catch (Exception e) {
                // Ignorer les champs qui n'existent pas
                System.err.println("Erreur lors du mapping du champ " + fieldName + ": " + e.getMessage());
            }
        }
        
        return instance;
    }
    
    /**
     * Groupe les paramètres par attribut de premier niveau
     * Ex: "e.name" -> "name", "e.departement[0].name" -> "departement"
     */
    private static Map<String, Map<String, String>> groupParametersByAttribute(Map<String, String[]> parameterMap, String prefix) {
        Map<String, Map<String, String>> grouped = new HashMap<>();
        String prefixWithDot = prefix + ".";
        
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefixWithDot)) {
                String remainingPath = key.substring(prefixWithDot.length());
                
                // Extraire le nom du premier attribut
                String firstAttribute = extractFirstAttribute(remainingPath);
                
                if (!grouped.containsKey(firstAttribute)) {
                    grouped.put(firstAttribute, new HashMap<>());
                }
                
                grouped.get(firstAttribute).put(key, entry.getValue()[0]);
            }
        }
        
        return grouped;
    }
    
    /**
     * Extrait le premier attribut d'un chemin
     * Ex: "name" -> "name", "departement[0].name" -> "departement"
     */
    private static String extractFirstAttribute(String path) {
        int dotIndex = path.indexOf('.');
        int bracketIndex = path.indexOf('[');
        
        if (dotIndex == -1 && bracketIndex == -1) {
            return path;
        } else if (dotIndex == -1) {
            return path.substring(0, bracketIndex);
        } else if (bracketIndex == -1) {
            return path.substring(0, dotIndex);
        } else {
            return path.substring(0, Math.min(dotIndex, bracketIndex));
        }
    }
    
    /**
     * Mappe la valeur d'un champ en fonction de son type
     */
    private static Object mapFieldValue(Field field, Map<String, String> fieldParams, String fieldPath) throws Exception {
        Class<?> fieldType = field.getType();
        
        // Si c'est une List
        if (List.class.isAssignableFrom(fieldType)) {
            return mapToList(field, fieldParams, fieldPath);
        }
        // Si c'est un type primitif ou String
        else if (isPrimitiveOrWrapper(fieldType) || fieldType == String.class) {
            String value = fieldParams.get(fieldPath);
            if (value != null) {
                return Convertor.detectAndCastValue(value);
            }
            return null;
        }
        // Si c'est un objet complexe
        else {
            return mapToNestedObject(fieldType, fieldParams, fieldPath);
        }
    }
    
    /**
     * Mappe vers une List
     */
    private static List<?> mapToList(Field field, Map<String, String> fieldParams, String fieldPath) throws Exception {
        // Obtenir le type générique de la liste
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType)) {
            return new ArrayList<>();
        }
        
        ParameterizedType paramType = (ParameterizedType) genericType;
        Class<?> elementType = (Class<?>) paramType.getActualTypeArguments()[0];
        
        // Grouper les paramètres par index
        Map<Integer, Map<String, String>> indexedParams = groupByIndex(fieldParams, fieldPath);
        
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < indexedParams.size(); i++) {
            if (indexedParams.containsKey(i)) {
                Map<String, String> elementParams = indexedParams.get(i);
                Object element = mapToNestedObject(elementType, elementParams, fieldPath + "[" + i + "]");
                list.add(element);
            }
        }
        
        return list;
    }
    
    /**
     * Groupe les paramètres par index pour une liste
     * Ex: "e.departement[0].name", "e.departement[1].name" -> {0: {...}, 1: {...}}
     */
    private static Map<Integer, Map<String, String>> groupByIndex(Map<String, String> params, String fieldPath) {
        Map<Integer, Map<String, String>> indexed = new HashMap<>();
        
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(fieldPath + "[")) {
                // Extraire l'index
                int startBracket = key.indexOf('[', fieldPath.length());
                int endBracket = key.indexOf(']', startBracket);
                
                if (startBracket != -1 && endBracket != -1) {
                    String indexStr = key.substring(startBracket + 1, endBracket);
                    try {
                        int index = Integer.parseInt(indexStr);
                        
                        if (!indexed.containsKey(index)) {
                            indexed.put(index, new HashMap<>());
                        }
                        
                        indexed.get(index).put(key, entry.getValue());
                    } catch (NumberFormatException e) {
                        // Ignorer les indices invalides
                    }
                }
            }
        }
        
        return indexed;
    }
    
    /**
     * Mappe vers un objet imbriqué
     */
    private static Object mapToNestedObject(Class<?> objectType, Map<String, String> params, String prefix) throws Exception {
        Object instance = objectType.getDeclaredConstructor().newInstance();
        
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix + ".")) {
                String remainingPath = key.substring(prefix.length() + 1);
                String fieldName = extractFirstAttribute(remainingPath);
                
                try {
                    Field field = findField(objectType, fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        
                        // Si le champ est primitif/String, on assigne directement
                        if (isPrimitiveOrWrapper(field.getType()) || field.getType() == String.class) {
                            String fullPath = prefix + "." + fieldName;
                            if (params.containsKey(fullPath)) {
                                Object value = Convertor.detectAndCastValue(params.get(fullPath));
                                field.set(instance, value);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erreur lors du mapping du champ imbriqué " + fieldName + ": " + e.getMessage());
                }
            }
        }
        
        return instance;
    }
    
    /**
     * Cherche un champ dans la classe (y compris les champs hérités)
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }
    
    /**
     * Vérifie si un type est primitif ou wrapper
     */
    private static boolean isPrimitiveOrWrapper(Class<?> type) {
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
