
package framework;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) // Marche dynamiquement
@Target(ElementType.METHOD) // Marche pour les fonctions
public @interface UrlAnnotation 
{
    String url();
}
