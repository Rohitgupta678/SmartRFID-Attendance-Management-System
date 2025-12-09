
import java.io.*;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.DBConnection;

@WebServlet("/SubjectDetailsServlet")
public class SubjectDetailsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        HttpSession session = request.getSession(false);

        try (PrintWriter out = response.getWriter()) {

            if (session == null || session.getAttribute("username") == null) {
                response.sendRedirect("login.html");
                return;
            }

            String username = (String) session.getAttribute("username");
            String role = (String) session.getAttribute("role");
            if (!"student".equalsIgnoreCase(role)) {
                response.sendRedirect("login.html");
                return;
            }

            int subjectId = Integer.parseInt(request.getParameter("subjectId"));
            Connection con = DBConnection.getConnection();

            // Get student_id
            int studentId = -1;
            String studentQuery = "SELECT s.student_id FROM students s "
                    + "JOIN users u ON s.user_id=u.user_id WHERE u.username=?";
            try (PreparedStatement ps = con.prepareStatement(studentQuery)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) studentId = rs.getInt("student_id");
            }

            String subjectName = "";
            try (PreparedStatement ps = con.prepareStatement("SELECT name FROM subjects WHERE subject_id=?")) {
                ps.setInt(1, subjectId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) subjectName = rs.getString("name");
            }

            out.println("<html><head><title>Subject Attendance</title>");
            out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css' rel='stylesheet'>");
            out.println("</head><body class='bg-light'>");

            out.println("<div class='container mt-4'><h3 class='text-center text-primary mb-3'>"
                    + subjectName + " - Attendance Details</h3>");

            out.println("<table class='table table-striped table-bordered'><thead class='table-primary'>"
                    + "<tr><th>Date</th><th>Time</th><th>Status</th><th>Topic</th><th>Faculty</th></tr></thead><tbody>");

            String sql = "SELECT a.date, a.time, a.status, sy.topic_title, u.name AS teacher_name "
                    + "FROM attendance a "
                    + "JOIN subjects s ON a.subject_id = s.subject_id "
                    + "LEFT JOIN teachers t ON s.teacher_id = t.teacher_id "
                    + "LEFT JOIN users u ON t.user_id = u.user_id "
                    + "LEFT JOIN syllabus sy ON s.subject_id = sy.subject_id "
                    + "WHERE a.student_id = ? AND a.subject_id = ? "
                    + "ORDER BY a.date DESC";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, subjectId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String date = rs.getString("date");
                    String time = rs.getString("time");
                    String status = rs.getString("status");
                    String topic = rs.getString("topic_title");
                    String teacher = rs.getString("teacher_name");
                    String rowClass = "Present".equals(status) ? "table-success" : "table-danger";
                    out.println("<tr class='" + rowClass + "'><td>" + date + "</td><td>" + time + "</td><td>"
                            + status + "</td><td>" + (topic != null ? topic : "-") + "</td><td>"
                            + (teacher != null ? teacher : "-") + "</td></tr>");
                }
            }

            out.println("</tbody></table>");
            out.println("<div class='text-center'><a href='StudentAttendanceServlet' class='btn btn-outline-primary'>Back to Dashboard</a></div>");
            out.println("</div></body></html>");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
