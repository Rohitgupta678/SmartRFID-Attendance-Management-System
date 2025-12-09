import model.DBConnection;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/Attendance")
public class Attendance extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // Accept both GET and POST
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    // Handles all attendance functionality: student mark/view, teacher manage/save
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null || session.getAttribute("role") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String username = (String) session.getAttribute("username");
        String role = (String) session.getAttribute("role");
        String action = request.getParameter("action"); // "mark", "view", "manage", "save"
        String subjectIdStr = request.getParameter("subject_id");
        int subjectId = -1;
        if (subjectIdStr != null && !subjectIdStr.trim().isEmpty()) {
            try {
                subjectId = Integer.parseInt(subjectIdStr);
            } catch (NumberFormatException ignored) {
            }
        }

        try (Connection con = DBConnection.getConnection()) {
            if ("student".equalsIgnoreCase(role)) {
                handleStudentAttendance(request, response, con, username, action, subjectId);
            } else if ("teacher".equalsIgnoreCase(role)) {
                handleTeacherAttendance(request, response, con, username, action, subjectId);
            } else {
                response.getWriter().println("<h3>Access denied: Unsupported role.</h3>");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    // ---------- Student side ----------
    private void handleStudentAttendance(HttpServletRequest request, HttpServletResponse response, Connection con,
            String username, String action, int subjectId) throws Exception {

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
            sendHtmlResponse(response, "<h3>You are not registered as a student!</h3>");
            return;
        }

        if ("mark".equalsIgnoreCase(action)) {
            if (subjectId <= 0) {
                sendHtmlResponse(response, "<h3>Invalid subject. Please contact your teacher.</h3>");
                return;
            }

            String checkSql = "SELECT COUNT(*) FROM attendance WHERE student_id=? AND subject_id=? AND date=CURDATE()";
            try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, subjectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        sendHtmlResponse(response, "<h3>Attendance already marked today for this subject.</h3>"
                                + "<a href='attendance.jsp' class='btn btn-primary'>Go Back</a>");
                        return;
                    }
                }
            }

            String insertSql = "INSERT INTO attendance (student_id, date, status, subject_id) VALUES (?, CURDATE(), 'Present', ?)";
            try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, subjectId);
                ps.executeUpdate();
            }

            sendHtmlResponse(response, "<h3>Attendance marked successfully for today!</h3>"
                    + "<a href='attendance.jsp' class='btn btn-primary'>Go Back</a>");
            return;
        }

        if ("view".equalsIgnoreCase(action)) {
            // Show student attendance details for a subject (subjectId param required)
            if (subjectId <= 0) {
                response.sendRedirect("attendance.jsp");
                return;
            }

            String sql = "SELECT a.date, a.status, s.name AS subject_name "
                    + "FROM attendance a JOIN subjects s ON a.subject_id = s.subject_id "
                    + "WHERE a.student_id=? AND a.subject_id=? ORDER BY a.date DESC";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, subjectId);
                try (ResultSet rs = ps.executeQuery()) {
                    request.setAttribute("attendanceResultSet", rsToList(rs));
                    request.setAttribute("subjectId", subjectId);
                    // forward to JSP that reads request attributes
                    request.getRequestDispatcher("studentAttendanceDetails.jsp").forward(request, response);
                    return;
                }
            }
        }

        // default redirect to dashboard
        response.sendRedirect("attendance.jsp");
    }

    // ---------- Teacher side ----------
    private void handleTeacherAttendance(HttpServletRequest request, HttpServletResponse response, Connection con,
            String username, String action, int subjectId) throws Exception {

        // Resolve teacher_id
        int teacherId = -1;
        String getTeacherSql = "SELECT t.teacher_id FROM teachers t JOIN users u ON t.user_id = u.user_id WHERE u.username = ?";
        try (PreparedStatement ps = con.prepareStatement(getTeacherSql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    teacherId = rs.getInt("teacher_id");
                }
            }
        }
        if (teacherId == -1) {
            sendHtmlResponse(response, "<h3>You are not registered as a teacher!</h3>");
            return;
        }

        if ("manage".equalsIgnoreCase(action)) {
            // Show manageAttendance.jsp — teacher selects date and subject and edits statuses
            // We will fetch the students and any existing attendance for that date & subject
            String dateParam = request.getParameter("date");
            Date date;
            if (dateParam == null || dateParam.isEmpty()) {
                date = new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateParam);
            }

            // Get students list (all students or you may filter by teacher's stream/course)
            String getStudents = "SELECT s.student_id, u.name, u.username, s.roll_no FROM students s JOIN users u ON s.user_id = u.user_id ORDER BY u.name";
            List<java.util.Map<String, Object>> students = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(getStudents);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("student_id", rs.getInt("student_id"));
                    m.put("name", rs.getString("name"));
                    m.put("username", rs.getString("username"));
                    m.put("roll_no", rs.getString("roll_no"));
                    students.add(m);
                }
            }

            // Get subjects assigned to this teacher
            String getSubjects = "SELECT subject_id, name FROM subjects WHERE teacher_id = ?";
            List<java.util.Map<String, Object>> subjects = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(getSubjects)) {
                ps.setInt(1, teacherId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        java.util.Map<String, Object> sub = new java.util.HashMap<>();
                        sub.put("subject_id", rs.getInt("subject_id"));
                        sub.put("name", rs.getString("name"));
                        subjects.add(sub);
                    }
                }
            }

            // Forward attributes to JSP
            request.setAttribute("students", students);
            request.setAttribute("subjects", subjects);
            request.setAttribute("date", date.toString());
            request.setAttribute("subjectId", subjectId);
            request.getRequestDispatcher("manageAttendance.jsp").forward(request, response);
            return;
        }

        if ("save".equalsIgnoreCase(action)) {
            // Save attendance statuses posted by teacher
            String dateParam = request.getParameter("date");
            Date date;
            if (dateParam == null || dateParam.isEmpty()) {
                date = new Date(System.currentTimeMillis());
            } else {
                date = Date.valueOf(dateParam);
            }

            String subjectParam = request.getParameter("subject_id");
            if (subjectParam == null || subjectParam.isEmpty()) {
                sendHtmlResponse(response, "<h3>Please select a subject.</h3><a href='attendanceteacher.jsp'>Back</a>");
                return;
            }
            int sId = Integer.parseInt(subjectParam);

            // iterate all students in DB and read status_#{studentId} from request
            String getStudentsSql = "SELECT s.student_id FROM students s";
            try (PreparedStatement ps = con.prepareStatement(getStudentsSql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sid = rs.getInt("student_id");
                    String statusParam = request.getParameter("status_" + sid);
                    if (statusParam == null) {
                        continue; // no input for that student
                    }
                    // check if exists
                    String checkSql = "SELECT id FROM attendance WHERE student_id=? AND subject_id=? AND date=?";
                    try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                        psCheck.setInt(1, sid);
                        psCheck.setInt(2, sId);
                        psCheck.setDate(3, date);
                        try (ResultSet rsCheck = psCheck.executeQuery()) {
                            if (rsCheck.next()) {
                                int attId = rsCheck.getInt("id");
                                String updateSql = "UPDATE attendance SET status=? WHERE id=?";
                                try (PreparedStatement psUpd = con.prepareStatement(updateSql)) {
                                    psUpd.setString(1, statusParam);
                                    psUpd.setInt(2, attId);
                                    psUpd.executeUpdate();
                                }
                            } else {
                                String insertSql = "INSERT INTO attendance (student_id, date, status, subject_id) VALUES (?, ?, ?, ?)";
                                try (PreparedStatement psIns = con.prepareStatement(insertSql)) {
                                    psIns.setInt(1, sid);
                                    psIns.setDate(2, date);
                                    psIns.setString(3, statusParam);
                                    psIns.setInt(4, sId);
                                    psIns.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }
            response.sendRedirect("Attendance?action=manage&subject_id=" + sId);
            return;
        }

        // default redirect to teacher dashboard
        response.sendRedirect("attendanceteacher.jsp");
    }

    // Utility: convert a ResultSet into List<Map<String,Object>> so JSPs can iterate
    private List<java.util.Map<String, Object>> rsToList(ResultSet rs) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        while (rs.next()) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }

    private void sendHtmlResponse(HttpServletResponse response, String bodyContent) throws IOException {
        response.setContentType("text/html");
        response.getWriter().println("<html><head><title>Attendance</title>");
        response.getWriter().println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
        response.getWriter().println("</head><body class='p-4'>");
        response.getWriter().println("<div class='container'>");
        response.getWriter().println(bodyContent);
        response.getWriter().println("</div></body></html>");
    }
}