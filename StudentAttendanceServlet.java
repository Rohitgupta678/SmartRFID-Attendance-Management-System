
import java.io.*;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.DBConnection;

@WebServlet("/StudentAttendanceServlet")
public class StudentAttendanceServlet extends HttpServlet {

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

            Connection con = DBConnection.getConnection();
            int studentId = -1;
            int userId = -1;
            String studentName = "";
            String rollNo = "";
            String course = "";
            String profileImage = "profile_images/default.png";

            // ---- Fetch student details ----
            String studentQuery = "SELECT s.student_id, u.user_id, u.name, s.roll_no, s.course "
                    + "FROM students s JOIN users u ON s.user_id = u.user_id "
                    + "WHERE u.username = ?";
            try (PreparedStatement ps = con.prepareStatement(studentQuery)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    studentId = rs.getInt("student_id");
                    userId = rs.getInt("user_id");
                    studentName = rs.getString("name");
                    rollNo = rs.getString("roll_no");
                    course = rs.getString("course");
                }
            }

            // ---- Fetch profile image ----
            String imgQuery = "SELECT file_name FROM profile_images WHERE user_id=?";
            try (PreparedStatement ps = con.prepareStatement(imgQuery)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    profileImage = "profile_images/" + rs.getString("file_name");
                }
            }

            if (studentId == -1) {
                out.println("<h3>Student record not found.</h3>");
                return;
            }

            // ---- Subject-wise attendance ----
            String sql = "SELECT sub.subject_id, sub.name AS subject_name, "
                    + "COUNT(a.id) AS total, "
                    + "SUM(CASE WHEN a.status='Present' THEN 1 ELSE 0 END) AS present "
                    + "FROM subjects sub "
                    + "LEFT JOIN attendance a ON sub.subject_id = a.subject_id AND a.student_id=? "
                    + "GROUP BY sub.subject_id, sub.name";

            int totalAll = 0, presentAll = 0;

            out.println("<!DOCTYPE html><html lang='en'><head>");
            out.println("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'>");
            out.println("<title>Student Dashboard</title>");
            out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css' rel='stylesheet'>");
            out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css' rel='stylesheet'>");
            out.println("</head><body class='bg-light'>");

            // Navbar
            out.println("<nav class='navbar navbar-expand-lg navbar-dark bg-primary shadow-sm'>"
                    + "<div class='container-fluid'>"
                    + "<a class='navbar-brand d-flex align-items-center' href='#'>"
                    + "<img src='" + profileImage + "' class='rounded-circle me-2' width='40' height='40'> "
                    + studentName + "</a>"
                    + "<div class='collapse navbar-collapse' id='nav'><ul class='navbar-nav ms-auto'>"
                    + "<li class='nav-item'><a class='nav-link' href='#' data-bs-toggle='modal' data-bs-target='#profileModal'><i class='bi bi-person'></i> Profile</a></li>"
                    + "<li class='nav-item'><a class='nav-link text-danger' href='logout'><i class='bi bi-box-arrow-right'></i> Logout</a></li>"
                    + "</ul></div></div></nav>");

            out.println("<div class='container mt-4'>");
            out.println("<h2 class='text-center text-primary mb-4'><i class='bi bi-calendar-check'></i> Attendance Dashboard</h2>");
            out.println("<div class='row'>");

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String subjectName = rs.getString("subject_name");
                    int total = rs.getInt("total");
                    int present = rs.getInt("present");
                    totalAll += total;
                    presentAll += present;
                    int percent = total > 0 ? (int) ((present * 100.0) / total) : 0;
                    String progressClass = percent >= 75 ? "bg-success"
                            : percent >= 50 ? "bg-warning" : "bg-danger";

                    out.println("<div class='col-md-4 mb-4'><div class='card shadow-sm border-0'><div class='card-body'>"
                            + "<h5 class='card-title text-primary'><i class='bi bi-book'></i> " + subjectName + "</h5>"
                            + "<p>Total Lectures: <b>" + total + "</b><br>Attended: <b>" + present + "</b></p>"
                            + "<div class='progress mb-2'><div class='progress-bar " + progressClass + "' style='width:" + percent + "%'>" + percent + "%</div></div>"
                            + "<a href='SubjectDetailsServlet?subjectId=" + rs.getInt("subject_id") + "' class='btn btn-outline-primary btn-sm w-100'>View Details</a>"
                            + "</div></div></div>");
                }
            }

            out.println("</div>");

            // Overall summary
            int overallPercent = totalAll > 0 ? (int) ((presentAll * 100.0) / totalAll) : 0;
            out.println("<div class='card shadow-sm p-3 mb-5 bg-white rounded mt-4'>"
                    + "<h4 class='text-center text-primary'><i class='bi bi-bar-chart-line'></i> Overall Attendance</h4>"
                    + "<p class='text-center fs-5'>Attended <b>" + presentAll + "</b> / <b>" + totalAll
                    + "</b> Lectures (" + overallPercent + "%)</p></div></div>");

            // Profile Modal
            out.println("<div class='modal fade' id='profileModal' tabindex='-1'><div class='modal-dialog modal-lg'><div class='modal-content'>"
                    + "<div class='modal-header bg-primary text-white'><h5 class='modal-title'><i class='bi bi-person-circle'></i> Profile</h5>"
                    + "<button type='button' class='btn-close btn-close-white' data-bs-dismiss='modal'></button></div>"
                    + "<div class='modal-body'><div class='row'>"
                    + "<div class='col-md-4 text-center'><img src='" + profileImage + "' class='rounded-circle mb-3' width='150' height='150'>"
                    + "<form action='UploadProfileImage' method='post' enctype='multipart/form-data'>"
                    + "<input type='file' name='profileImage' class='form-control mb-2'>"
                    + "<button type='submit' class='btn btn-outline-primary btn-sm'>Upload</button></form></div>"
                    + "<div class='col-md-8'><p><b>Name:</b> " + studentName + "<br><b>Roll No:</b> " + rollNo
                    + "<br><b>Course:</b> " + course + "</p></div>"
                    + "</div></div></div></div></div>");

            out.println("<footer class='bg-primary text-white text-center py-3 mt-5'>© 2025 RFID Smart Attendance System</footer>");
            out.println("<script src='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js'></script>");
            out.println("</body></html>");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
