import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.DBConnection;

@WebServlet("/StudentAttendanceDetailsServlet")
public class StudentAttendanceDetailsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        HttpSession session = request.getSession(false);

        if (session == null) {
            response.sendRedirect("login.html");
            return;
        }

        String username = (String) session.getAttribute("username");
        String role = (String) session.getAttribute("role");

        if (username == null || !"student".equalsIgnoreCase(role)) {
            response.sendRedirect("login.html");
            return;
        }

        // Get subject_id from request parameter
        String subjectIdStr = request.getParameter("subject_id");
        int subjectId = -1;
        if (subjectIdStr != null && !subjectIdStr.trim().isEmpty()) {
            try {
                subjectId = Integer.parseInt(subjectIdStr);
            } catch (NumberFormatException ignored) {
            }
        }

        if (subjectId <= 0) {
            response.sendRedirect("StudentAttendance"); // Redirect back if invalid
            return;
        }

        Connection con = null;
        List<Map<String, Object>> attendance = new ArrayList<>();
        String subjectName = "";

        try {
            con = DBConnection.getConnection();

            // Resolve student_id from username
            int studentId = -1;
            String sqlStudent = "SELECT s.student_id FROM students s JOIN users u ON s.user_id = u.user_id WHERE u.username = ?";
            try (PreparedStatement ps = con.prepareStatement(sqlStudent)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        studentId = rs.getInt("student_id");
                    }
                }
            }

            if (studentId == -1) {
                // Error response
                response.getWriter().println("<h3>You are not registered as a student.</h3>");
                return;
            }

            // Fetch attendance details
            String sql = "SELECT a.date, a.status, s.name AS subject_name "
                    + "FROM attendance a JOIN subjects s ON a.subject_id = s.subject_id "
                    + "WHERE a.student_id=? AND a.subject_id=? ORDER BY a.date DESC";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, subjectId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("date", rs.getString("date"));
                        row.put("status", rs.getString("status"));
                        row.put("subject_name", rs.getString("subject_name"));
                        attendance.add(row);
                        if (subjectName.isEmpty()) {
                            subjectName = rs.getString("subject_name");
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (con != null) con.close();
            } catch (Exception ignored) {}
        }

        // Generate HTML response
        try (PrintWriter out = response.getWriter()) {
            out.println("<!doctype html>");
            out.println("<html lang='en'>");
            out.println("<head>");
            out.println("<meta charset='utf-8'>");
            out.println("<meta name='viewport' content='width=device-width, initial-scale=1'>");
            out.println("<title>Attendance Details</title>");
            out.println("<link href='CSS/bootstrap.min.css' rel='stylesheet'>");
            out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.0/font/bootstrap-icons.css' rel='stylesheet'>");
            out.println("<script src='JS/bootstrap.bundle.min.js'></script>");
            out.println("</head>");
            out.println("<body class='bg-light'>");

            // Top Navigation Bar
            out.println("<nav class='navbar navbar-expand-lg navbar-dark bg-primary'>");
            out.println("<div class='container-fluid'>");
            out.println("<a class='navbar-brand' href='#'><i class='bi bi-person-circle'></i> " + username + "</a>");
            out.println("<button class='navbar-toggler' type='button' data-bs-toggle='collapse' data-bs-target='#navbarNav'>");
            out.println("<span class='navbar-toggler-icon'></span></button>");
            out.println("<div class='collapse navbar-collapse' id='navbarNav'>");
            out.println("<ul class='navbar-nav ms-auto'>");
            out.println("<li class='nav-item'><a class='nav-link' href='studentDashboard.html'><i class='bi bi-house'></i> Dashboard</a></li>");
            out.println("<li class='nav-item'><a class='nav-link' href='profile.html'><i class='bi bi-person'></i> Profile</a></li>");
            out.println("<li class='nav-item'><a class='nav-link' href='logout'><i class='bi bi-box-arrow-right'></i> Logout</a></li>");
            out.println("</ul></div></div></nav>");

            out.println("<div class='container mt-4'>");
            out.println("<h2 class='text-center mb-4'><i class='bi bi-calendar-check'></i> Attendance Details for " + subjectName + "</h2>");

            if (attendance.isEmpty()) {
                out.println("<div class='alert alert-warning text-center'>No attendance records found for this subject.</div>");
            } else {
                out.println("<div class='table-responsive'>");
                out.println("<table class='table table-striped table-bordered'>");
                out.println("<thead class='table-dark'>");
                out.println("<tr><th><i class='bi bi-calendar'></i> Date</th><th><i class='bi bi-check-circle'></i> Status</th><th><i class='bi bi-book'></i> Subject</th></tr>");
                out.println("</thead>");
                out.println("<tbody>");
                for (Map<String, Object> r : attendance) {
                    String status = (String) r.get("status");
                    String statusClass = "Present".equalsIgnoreCase(status) ? "text-success" : "text-danger";
                    out.println("<tr>");
                    out.println("<td>" + r.get("date") + "</td>");
                    out.println("<td class='" + statusClass + "'><i class='bi bi-circle-fill'></i> " + status + "</td>");
                    out.println("<td>" + (r.get("subject_name") != null ? r.get("subject_name") : "") + "</td>");
                    out.println("</tr>");
                }
                out.println("</tbody>");
                out.println("</table>");
                out.println("</div>");
            }

            out.println("<div class='text-center mt-4'><a class='btn btn-secondary' href='StudentAttendance'><i class='bi bi-arrow-left'></i> Back to Dashboard</a></div>");
            out.println("</div>");

            // Footer
            out.println("<footer class='bg-primary text-white text-center py-3 mt-5'>");
            out.println("<p>&copy; 2023 RFID Attendance System. All rights reserved.</p>");
            out.println("</footer>");

            out.println("</body>");
            out.println("</html>");
        }
    }
}