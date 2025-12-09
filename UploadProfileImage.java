
import java.io.*;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;
import model.DBConnection;

@WebServlet("/UploadProfileImage")
@MultipartConfig
public class UploadProfileImage extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String username = (String) session.getAttribute("username");
        Part filePart = request.getPart("profileImage");
        String fileName = getFileName(filePart);

        // 🔹 Dynamic path inside deployed folder (e.g., /Project1/profile_images)
        String uploadPath = getServletContext().getRealPath("") + File.separator + "profile_images";
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdir();
        }

        // 🔹 Save new profile image if provided
        if (fileName != null && !fileName.trim().isEmpty()) {
            filePart.write(uploadPath + File.separator + fileName);

            try (Connection con = DBConnection.getConnection()) {
                int userId = -1;
                try (PreparedStatement ps = con.prepareStatement("SELECT user_id FROM users WHERE username=?")) {
                    ps.setString(1, username);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        userId = rs.getInt("user_id");
                    }
                }

                if (userId != -1) {
                    String checkQuery = "SELECT * FROM profile_images WHERE user_id=?";
                    boolean exists = false;
                    try (PreparedStatement ps = con.prepareStatement(checkQuery)) {
                        ps.setInt(1, userId);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            exists = true;
                        }
                    }

                    if (exists) {
                        try (PreparedStatement ps = con.prepareStatement("UPDATE profile_images SET file_name=? WHERE user_id=?")) {
                            ps.setString(1, fileName);
                            ps.setInt(2, userId);
                            ps.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement ps = con.prepareStatement("INSERT INTO profile_images (user_id, file_name) VALUES (?,?)")) {
                            ps.setInt(1, userId);
                            ps.setString(2, fileName);
                            ps.executeUpdate();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 🔹 Display student info page
        displayProfilePage(username, response);
    }

    private void displayProfilePage(String username, HttpServletResponse response) throws IOException {
        try (Connection con = DBConnection.getConnection();
                PrintWriter out = response.getWriter()) {

            int userId = -1;
            int studentId = -1;
            String profileImage = "default.png";
            String name = "", rollNo = "", course = "", semester = "";

            // 🔹 Get user_id
            try (PreparedStatement ps = con.prepareStatement("SELECT user_id FROM users WHERE username=?")) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    userId = rs.getInt("user_id");
                }
            }

            // 🔹 Get student info
            try (PreparedStatement ps = con.prepareStatement("SELECT student_id, name, roll_no, course, semester FROM students WHERE user_id=?")) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    studentId = rs.getInt("student_id");
                    name = rs.getString("name");
                    rollNo = rs.getString("roll_no");
                    course = rs.getString("course");
                    semester = rs.getString("semester");
                }
            }

            // 🔹 Get profile image
            try (PreparedStatement ps = con.prepareStatement("SELECT file_name FROM profile_images WHERE user_id=?")) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    profileImage = rs.getString("file_name");
                }
            }

            // 🔹 Get attendance summary
            String queryAttendance
                    = "SELECT COUNT(a.id) AS total_classes, "
                    + "SUM(CASE WHEN a.status='Present' THEN 1 ELSE 0 END) AS attended "
                    + "FROM attendance a "
                    + "WHERE a.student_id=?";
            int total = 0, attended = 0;
            try (PreparedStatement ps = con.prepareStatement(queryAttendance)) {
                ps.setInt(1, studentId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    total = rs.getInt("total_classes");
                    attended = rs.getInt("attended");
                }
            }

            double percentage = (total == 0) ? 0 : (attended * 100.0 / total);
            String progressColor = percentage >= 75 ? "bg-success" : (percentage >= 50 ? "bg-warning" : "bg-danger");

            // 🔹 HTML OUTPUT (Bootstrap 5)
            out.println("<!DOCTYPE html>");
            out.println("<html><head>");
            out.println("<meta charset='UTF-8'>");
            out.println("<meta name='viewport' content='width=device-width, initial-scale=1'>");
            out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
            out.println("<title>Student Profile</title>");
            out.println("<style>");
            out.println(".profile-container {max-width: 900px; margin: 50px auto;}");
            out.println(".profile-card {border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.1);}");
            out.println(".profile-img {width:150px; height:150px; object-fit:cover; border-radius:50%; border:4px solid #007bff;}");
            out.println("</style>");
            out.println("</head><body class='bg-light'>");

            out.println("<div class='container profile-container'>");
            out.println("<div class='card profile-card p-4'>");

            out.println("<div class='text-center mb-4'>");
            out.println("<img src='profile_images/" + profileImage + "' alt='Profile Image' class='profile-img mb-3'>");
            out.println("<h3 class='text-primary fw-bold'>" + name + "</h3>");
            out.println("<p class='text-muted mb-1'><strong>Roll No:</strong> " + rollNo + "</p>");
            out.println("<p class='text-muted'><strong>Course:</strong> " + course + " | <strong>Semester:</strong> " + semester + "</p>");
            out.println("</div>");

            out.println("<hr>");

            // 🔹 Attendance Summary
            out.println("<div class='mb-4'>");
            out.println("<h5 class='text-primary mb-2'>Overall Attendance Summary</h5>");
            out.println("<p>Attended <strong>" + attended + "/" + total + "</strong> Lectures (" + String.format("%.1f", percentage) + "%)</p>");
            out.println("<div class='progress' style='height:25px;'>");
            out.println("<div class='progress-bar " + progressColor + "' style='width:" + percentage + "%'>" + String.format("%.1f", percentage) + "%</div>");
            out.println("</div>");
            out.println("</div>");

            // 🔹 Upload Form
            out.println("<form method='post' action='UploadProfileImage' enctype='multipart/form-data' class='text-center mt-4'>");
            out.println("<div class='input-group' style='max-width:400px;margin:auto;'>");
            out.println("<input type='file' name='profileImage' class='form-control' required>");
            out.println("<button type='submit' class='btn btn-primary'>Upload</button>");
            out.println("</div>");
            out.println("</form>");

            out.println("<div class='text-center mt-4'>");
            out.println("<a href='StudentAttendanceServlet' class='btn btn-outline-secondary'>Back to Dashboard</a>");
            out.println("</div>");

            out.println("</div></div>");
            out.println("<script src='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js'></script>");
            out.println("</body></html>");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        String[] items = contentDisp.split(";");
        for (String s : items) {
            if (s.trim().startsWith("filename")) {
                return s.substring(s.indexOf('=') + 2, s.length() - 1);
            }
        }
        return null;
    }
}
