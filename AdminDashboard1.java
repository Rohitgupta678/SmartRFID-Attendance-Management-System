import model.DBConnection;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("AdminDashboard1")
public class AdminDashboard1 extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        String adminName = (session != null && session.getAttribute("adminName") != null)
                ? (String) session.getAttribute("adminName")
                : "Admin";

        int totalStudents = 0, totalTeachers = 0, totalCourses = 0;

        try (Connection conn = DBConnection.getConnection()) {
            // Count total students
            PreparedStatement ps1 = conn.prepareStatement("SELECT COUNT(*) FROM students");
            ResultSet rs1 = ps1.executeQuery();
            if (rs1.next()) totalStudents = rs1.getInt(1);

            // Count total teachers
            PreparedStatement ps2 = conn.prepareStatement("SELECT COUNT(*) FROM teachers");
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) totalTeachers = rs2.getInt(1);

            // Count total courses
            PreparedStatement ps3 = conn.prepareStatement("SELECT COUNT(*) FROM courses");
            ResultSet rs3 = ps3.executeQuery();
            if (rs3.next()) totalCourses = rs3.getInt(1);

        } catch (Exception e) {
            e.printStackTrace();
        }

        out.println("<!DOCTYPE html>");
        out.println("<html lang='en'><head>");
        out.println("<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'>");
        out.println("<title>Admin Dashboard</title>");
        out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css' rel='stylesheet'/>");
        out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css' rel='stylesheet'/>");
        out.println("<style>");
        out.println("body{background:linear-gradient(135deg,#a8edea,#fed6e3);min-height:100vh;font-family:'Segoe UI',Tahoma;}");
        out.println(".dashboard-card{border:none;border-radius:20px;transition:all 0.3s ease;box-shadow:0 4px 8px rgba(0,0,0,0.1);background:#fff;}");
        out.println(".dashboard-card:hover{transform:translateY(-8px);box-shadow:0 8px 16px rgba(0,0,0,0.15);}");
        out.println(".dashboard-icon{font-size:2.5rem;margin-bottom:15px;color:#0d6efd;}");
        out.println("</style></head><body>");

        // Navbar
        out.println("<nav class='navbar navbar-expand-lg navbar-dark bg-primary shadow-sm'>");
        out.println("<div class='container'><a class='navbar-brand fw-bold' href='#'><i class='bi bi-speedometer2'></i> Admin Dashboard</a>");
        out.println("<div class='collapse navbar-collapse' id='navbarNav'><ul class='navbar-nav ms-auto'>");
        out.println("<li class='nav-item'><a href='#' class='nav-link active'>Dashboard</a></li>");
        out.println("<li class='nav-item'><a href='#' class='nav-link'>Profile</a></li>");
        out.println("<li class='nav-item'><a href='#' class='nav-link'>Settings</a></li>");
        out.println("<li class='nav-item'><a href='logout' class='nav-link text-warning'><i class='bi bi-box-arrow-right'></i> Logout</a></li>");
        out.println("</ul></div></div></nav>");

        // Dashboard
        out.println("<div class='container my-5'>");
        out.println("<h2 class='text-center text-primary fw-bold mb-4'>Welcome, " + adminName + " 👋</h2>");

        // Summary stats
        out.println("<div class='row text-center mb-5'>");
        out.println("<div class='col-md-4'><div class='card p-3 shadow-sm'><h5>Total Students</h5><h3 class='text-primary'>" + totalStudents + "</h3></div></div>");
        out.println("<div class='col-md-4'><div class='card p-3 shadow-sm'><h5>Total Teachers</h5><h3 class='text-primary'>" + totalTeachers + "</h3></div></div>");
        out.println("<div class='col-md-4'><div class='card p-3 shadow-sm'><h5>Total Courses</h5><h3 class='text-primary'>" + totalCourses + "</h3></div></div>");
        out.println("</div>");

        // Cards Grid
        out.println("<div class='row g-4'>");

        // Manage Students
        out.println("<div class='col-md-4'><div class='card dashboard-card text-center p-4'>");
        out.println("<div class='dashboard-icon'><i class='bi bi-mortarboard'></i></div>");
        out.println("<h5 class='fw-bold'>Manage Students</h5>");
        out.println("<p class='text-muted'>Add, edit, or remove student records.</p>");
        out.println("<a href='ManageStudentsServlet' class='btn btn-primary w-100'>Go</a></div></div>");

        // Manage Teachers
        out.println("<div class='col-md-4'><div class='card dashboard-card text-center p-4'>");
        out.println("<div class='dashboard-icon'><i class='bi bi-person-badge'></i></div>");
        out.println("<h5 class='fw-bold'>Manage Teachers</h5>");
        out.println("<p class='text-muted'>Add, edit, and assign subjects to staff.</p>");
        out.println("<a href='ManageTeachersServlet' class='btn btn-primary w-100'>Go</a></div></div>");

        // Course & Subject
        out.println("<div class='col-md-4'><div class='card dashboard-card text-center p-4'>");
        out.println("<div class='dashboard-icon'><i class='bi bi-journal-bookmark'></i></div>");
        out.println("<h5 class='fw-bold'>Course & Subject Structure</h5>");
        out.println("<p class='text-muted'>Define, map, and manage courses.</p>");
        out.println("<a href='CourseSubjectServlet' class='btn btn-primary w-100'>Go</a></div></div>");

        // Attendance Review
        out.println("<div class='col-md-4'><div class='card dashboard-card text-center p-4'>");
        out.println("<div class='dashboard-icon'><i class='bi bi-calendar-check'></i></div>");
        out.println("<h5 class='fw-bold'>Attendance Audit & Review</h5>");
        out.println("<p class='text-muted'>Review and verify attendance records.</p>");
        out.println("<a href='AttendanceReviewServlet' class='btn btn-primary w-100'>Go</a></div></div>");

        // Role Access
        out.println("<div class='col-md-4'><div class='card dashboard-card text-center p-4'>");
        out.println("<div class='dashboard-icon'><i class='bi bi-shield-lock'></i></div>");
        out.println("<h5 class='fw-bold'>Role & Access Control</h5>");
        out.println("<p class='text-muted'>Manage user permissions and security roles.</p>");
        out.println("<a href='RoleAccessServlet' class='btn btn-primary w-100'>Go</a></div></div>");

        // Timetable
        out.println("<div class='col-md-4'><div class='card dashboard-card text-center p-4'>");
        out.println("<div class='dashboard-icon'><i class='bi bi-clock-history'></i></div>");
        out.println("<h5 class='fw-bold'>Master Timetables</h5>");
        out.println("<p class='text-muted'>Create and manage institutional schedules.</p>");
        out.println("<a href='TimetableServlet' class='btn btn-primary w-100'>Go</a></div></div>");

        out.println("</div></div>");
        out.println("<footer class='bg-primary text-white text-center py-3 mt-5'>© 2025 RFID Smart Attendance System — Admin Panel</footer>");
        out.println("<script src='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js'></script>");
        out.println("</body></html>");
    }
}
