
import model.DBConnection;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/StudentDashboard1")
public class StudentDashboard1 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null && "student".equalsIgnoreCase((String) session.getAttribute("role"))) {
            String username = (String) session.getAttribute("username");
            String role = (String) session.getAttribute("role");

            response.setContentType("text/html");
            PrintWriter out = response.getWriter();

            out.println("<!DOCTYPE html>");
            out.println("<html><head>");
            out.println("<title>Student Dashboard</title>");
            out.println("<link rel='stylesheet' href='css/bootstrap.min.css'>");
            out.println("</head><body class='container'>");

            out.println("<h2>Welcome, " + username + "!</h2>");
            out.println("<h4>Role: " + role + "</h4>");
            out.println("<hr/>");

            out.println("<ul class='list-group'>");
            out.println("<li class='list-group-item'><a href='AttendanceServlet'>📅 View/Mark Attendance</a></li>");
            out.println("<li class='list-group-item'><a href='SyllabusServlet'>📘 View Syllabus</a></li>");
            out.println("<li class='list-group-item'><a href='NotesServlet'>📝 Download Notes</a></li>");
            out.println("<li class='list-group-item'><a href='VideoServlet'>🎥 Watch Videos</a></li>");
            out.println("<li class='list-group-item'><a href='QuizServlet'>🧩 Take Quiz</a></li>");
            out.println("<li class='list-group-item'><a href='QuizResultServlet'>📊 View Quiz Results</a></li>");
            out.println("</ul>");

            out.println("<hr/>");
            out.println("<a href='LogoutServlet' class='btn btn-danger'>Logout</a>");

            out.println("</body></html>");
        } else {
            response.sendRedirect("login.html");
        }
    }
}
