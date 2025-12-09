
import model.DBConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/Login")
public class Login extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // When user directly visits /Login
        response.sendRedirect("login.html");   // open the login form
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        try {
            Connection con = DBConnection.getConnection();
            String sql = "SELECT role FROM users WHERE LOWER(username) = LOWER(?) AND LOWER(password) = LOWER(?)";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String role = rs.getString("role");

                HttpSession session = request.getSession();
                session.setAttribute("username", username);
                session.setAttribute("role", role);

                // Redirect based on role
                if ("student".equalsIgnoreCase(role)) {
                    response.sendRedirect("studentDashboard.html");
                } else if ("teacher".equalsIgnoreCase(role)) {
                    // Redirect to TeacherDashboard1 servlet instead of directly to HTML
                    response.sendRedirect("TeacherDashboard1");
                } else if ("admin".equalsIgnoreCase(role)) {
                    response.sendRedirect("adminDashboard.html");
                } else {
                    out.println("<h3>Invalid role assigned!</h3>");
                    out.println("<a href='login.html'>Try again</a>");
                }
            } else {
                out.println("<h3>Invalid Username or Password!</h3>");
                out.println("<a href='login.html'>Try again</a>");
            }

            con.close();
        } catch (Exception e) {
            e.printStackTrace();
            out.println("<h3>Error: " + e.getMessage() + "</h3>");
        }
    }
}
