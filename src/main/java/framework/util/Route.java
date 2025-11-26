package framework.models;

import java.lang.reflect.Method;

public class Route {
    private Class<?> clazz;
    private Method method;
    private String methodHTTP;
    private String url;

    public Route(Class<?> clazz, Method method, String methodHTTP, String url) {
        this.clazz = clazz;
        this.method = method;
        this.methodHTTP = methodHTTP;
        this.url = url;
    }

    public Route(Class<?> clazz, Method method, String methodHTTP) {
        this.clazz = clazz;
        this.method = method;
        this.methodHTTP = methodHTTP;
    }

    public Class<?> getClazz() { return clazz; }
    public void setClazz(Class<?> clazz) { this.clazz = clazz; }

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }

    public String getMethodHTTP() { return methodHTTP; }
    public void setMethodHTTP(String methodHTTP) { this.methodHTTP = methodHTTP; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}