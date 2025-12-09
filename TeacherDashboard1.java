
import model.DBConnection;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/TeacherDashboard1")
public class TeacherDashboard1 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null && "teacher".equalsIgnoreCase((String) session.getAttribute("role"))) {
            String username = (String) session.getAttribute("username");
            String role = (String) session.getAttribute("role");

            // URL encode parameters to be safe
            String encodedUsername = java.net.URLEncoder.encode(username, "UTF-8");
            String encodedRole = java.net.URLEncoder.encode(role, "UTF-8");

            response.sendRedirect("teacherDashboard.html?username=" + encodedUsername + "&role=" + encodedRole);
        } else {
            response.sendRedirect("login.html");
        }
    }
}
