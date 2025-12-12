package framework.util;

public class Convertor
{
    public static Object detectAndCastValue(String value) {
        if (value == null) {
            return null;
        }

        // Gestion des booléens (true, false, on, off)
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")) {
            return false;
        }

        try {
            // Gestion des nombres entiers
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Ignorer si ce n'est pas un entier
        }

        try {
            // Gestion des nombres décimaux
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Ignorer si ce n'est pas un double
        }

        try {
            // Gestion des dates et heures (ISO 8601)
            return java.time.LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            // Ignorer si ce n'est pas une date et heure valide
        }

        try {
            // Gestion des dates simples (ISO 8601)
            return java.time.LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            // Ignorer si ce n'est pas une date valide
        }

        // Retourne la chaîne brute si aucun type ne correspond
        return value;
    }
}