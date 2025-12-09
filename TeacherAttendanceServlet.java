import java.io.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.DBConnection;

@WebServlet("/TeacherAttendanceServlet") // match web.xml
public class TeacherAttendanceServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String action = request.getParameter("action");
        String subjectParam = request.getParameter("subject_id");
        Integer subjectId = null;

        // ✅ Only parse subject_id if it's not null/empty
        if (subjectParam != null && !subjectParam.trim().isEmpty()) {
            try {
                subjectId = Integer.parseInt(subjectParam);
            } catch (NumberFormatException e) {
                subjectId = null; // invalid number, treat as missing
            }
        }

        // ✅ If no action provided, show default dashboard message
        if (action == null || !"manage".equalsIgnoreCase(action) || subjectId == null) {
            out.println("<html><body style='font-family: Arial; text-align:center; margin-top:100px;'>");
            out.println("<h2 style='color:red;'>Invalid or Missing Parameters!</h2>");
            out.println("<p><a href='TeacherDashboard1' style='text-decoration:none; color:green;'>Return to Teacher Dashboard</a></p>");
            out.println("</body></html>");
            return;
        }

        // ✅ Continue only when action=manage and subject_id is valid
        try (Connection con = DBConnection.getConnection()) {

            // Fetch subject name
            String subjectName = "";
            try (PreparedStatement ps1 = con.prepareStatement("SELECT name FROM subjects WHERE subject_id = ?")) {
                ps1.setInt(1, subjectId);
                ResultSet rs1 = ps1.executeQuery();
                if (rs1.next()) {
                    subjectName = rs1.getString("name");
                }
            }

            // Fetch all students
            PreparedStatement ps2 = con.prepareStatement("SELECT * FROM students");
            ResultSet rs2 = ps2.executeQuery();

            out.println("<html><head><title>Manage Attendance</title>");
            out.println("<link href='CSS/bootstrap.min.css' rel='stylesheet'>");
            out.println("</head><body class='bg-light'>");

            out.println("<div class='container mt-5'>");
            out.println("<h2 class='text-center mb-4 text-success'>" + subjectName + " Attendance</h2>");
            out.println("<form method='post' action='TeacherAttendanceServlet'>");
            out.println("<input type='hidden' name='subject_id' value='" + subjectId + "'>");

            // Date and Time Picker
            out.println("<div class='mb-3'>");
            out.println("<label class='form-label'>Date:</label>");
            out.println("<input type='date' name='date' class='form-control' required>");
            out.println("</div>");
            out.println("<div class='mb-3'>");
            out.println("<label class='form-label'>Time:</label>");
            out.println("<input type='time' name='time' class='form-control' required>");
            out.println("</div>");

            // Student table
            out.println("<table class='table table-bordered table-striped'>");
            out.println("<thead class='table-success'><tr><th>Roll No</th><th>Course</th><th>RFID UID</th><th>Present</th></tr></thead><tbody>");
            while (rs2.next()) {
                int studentId = rs2.getInt("student_id");
                String rollNo = rs2.getString("roll_no");
                String course = rs2.getString("course");
                String rfid = rs2.getString("rfid_uid");

                out.println("<tr>");
                out.println("<td>" + rollNo + "</td>");
                out.println("<td>" + course + "</td>");
                out.println("<td>" + rfid + "</td>");
                out.println("<td><input type='checkbox' name='present' value='" + studentId + "'></td>");
                out.println("</tr>");
            }
            out.println("</tbody></table>");

            out.println("<button type='submit' class='btn btn-success w-100'>Submit Attendance</button>");
            out.println("</form></div></body></html>");

        } catch (Exception e) {
            e.printStackTrace(out);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try (Connection con = DBConnection.getConnection()) {
            String[] presentIds = request.getParameterValues("present");
            String date = request.getParameter("date");
            String time = request.getParameter("time");
            String subjectParam = request.getParameter("subject_id");

            if (subjectParam == null || subjectParam.isEmpty()) {
                response.sendRedirect("TeacherDashboard1");
                return;
            }

            int subjectId = Integer.parseInt(subjectParam);

            if (presentIds != null) {
                for (String sid : presentIds) {
                    PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO attendance (student_id, date, time, status, subject_id) VALUES (?, ?, ?, ?, ?)"
                    );
                    ps.setInt(1, Integer.parseInt(sid));
                    ps.setString(2, date);
                    ps.setString(3, time);
                    ps.setString(4, "Present");
                    ps.setInt(5, subjectId);
                    ps.executeUpdate();
                }
            }

            response.sendRedirect("TeacherDashboard1");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
