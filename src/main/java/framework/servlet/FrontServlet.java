package framework.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.http.HttpClient;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.RequestDispatcher;

@WebServlet("/")
public class FrontServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rep) throws ServletException, IOException
    {
        customRedirect(req, rep);
    }

    private void customRedirect(HttpServletRequest req, HttpServletResponse rep) throws IOException, ServletException
    {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        boolean pathExists = getServletContext().getResource(path) != null;

        if(pathExists && !path.equals("/") && !path.isEmpty()) 
        {
            RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("default");
            dispatcher.forward(req, rep);
        } 
        // Introuvable
        else 
        {
            PrintWriter writer = rep.getWriter();
            writer.println("Chemin introuvable : ");
            writer.println(path);
        }
    }
}
