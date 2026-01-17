package framework.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ProjectConfig {
    
    private Properties properties;
    private String configFilePath;
    
    /**
     * Constructeur qui charge le fichier de configuration depuis le classpath
     */
    public ProjectConfig() {
        this.properties = new Properties();
        this.configFilePath = "app.properties";
        loadFromClasspath();
    }
    
    /**
     * Constructeur avec chemin personnalisé
     */
    public ProjectConfig(String configPath) {
        this.properties = new Properties();
        this.configFilePath = configPath;
        loadFromFile();
    }
    
    /**
     * Charge la configuration depuis le classpath
     */
    private void loadFromClasspath() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFilePath)) {
            if (input == null) {
                System.out.println("Fichier de configuration non trouvé: " + configFilePath);
                return;
            }
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Charge la configuration depuis un fichier
     */
    private void loadFromFile() {
        try (FileInputStream input = new FileInputStream(configFilePath)) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Récupère une propriété
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Récupère une propriété avec une valeur par défaut
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Définit une propriété
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    /**
     * Sauvegarde la configuration dans un fichier
     */
    public void save(String filePath) {
        try (FileOutputStream output = new FileOutputStream(filePath)) {
            properties.store(output, "Project Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Affiche toutes les propriétés
     */
    public void printAll() {
        properties.forEach((key, value) -> 
            System.out.println(key + " = " + value)
        );
    }
}