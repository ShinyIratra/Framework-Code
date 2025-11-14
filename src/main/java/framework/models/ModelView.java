package framework.models;

import java.util.HashMap;

public class ModelView
{
    private String view;
    private HashMap<String, Object> attributes = new HashMap<>();

    public ModelView(String view)
    {
        this.view = view;
    }

    public String getView()
    {
        return view;
    }

    public void setView(String view)
    {
        this.view = view;
    }

    public void addAttribute(String key, Object value)
    {
        this.attributes.put(key, value);
    }

    public HashMap<String, Object> getAttributes()
    {
        return attributes;
    }

    public Object getAttribute(String key)
    {
        return this.attributes.get(key);
    }
}