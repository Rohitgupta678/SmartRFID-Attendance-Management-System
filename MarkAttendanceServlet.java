import java.io.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.google.gson.*;
import model.DBConnection;
import javax.servlet.annotation.WebServlet;

@WebServlet("/MarkAttendanceServlet")
public class MarkAttendanceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    static class AttendanceRecord {
        String student_id;
        String status;
    }

    static class Payload {
        String subject_id;
        String stream_id;
        String date;
        String time;
        List<AttendanceRecord> records;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // session + role check
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("role") == null ||
                !"teacher".equalsIgnoreCase((String) session.getAttribute("role"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":false,\"message\":\"Access denied\"}");
            return;
        }

        String body = new BufferedReader(new InputStreamReader(req.getInputStream()))
                .lines().reduce("", (a,b) -> a + b);

        Payload p;
        try {
            p = gson.fromJson(body, Payload.class);
        } catch (JsonSyntaxException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":false,\"message\":\"Invalid JSON\"}");
            return;
        }

        if (p == null || p.records == null || p.records.isEmpty()
                || p.subject_id == null || p.stream_id == null || p.date == null || p.time == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":false,\"message\":\"Missing required fields\"}");
            return;
        }

        int subjectId, streamId;
        try {
            subjectId = Integer.parseInt(p.subject_id);
            streamId = Integer.parseInt(p.stream_id);
        } catch (NumberFormatException ex) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":false,\"message\":\"Invalid subject_id or stream_id\"}");
            return;
        }

        String date = p.date;
        String time = p.time;
        String teacherId = (String) session.getAttribute("teacher_id"); // may be null if not set in session

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            // Check statement: find if attendance row exists for student/subject/date/time
            String checkSql = "SELECT attendance_id, status FROM attendance WHERE student_id = ? AND subject_id = ? AND date = ? AND time = ?";
            String insertSql = "INSERT INTO attendance (student_id, teacher_id, subject_id, stream_id, date, time, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
            String updateSql = "UPDATE attendance SET status = ?, teacher_id = ? WHERE attendance_id = ?";

            try (PreparedStatement checkPs = con.prepareStatement(checkSql);
                 PreparedStatement insertPs = con.prepareStatement(insertSql);
                 PreparedStatement updatePs = con.prepareStatement(updateSql)) {

                for (AttendanceRecord rec : p.records) {
                    int studentId;
                    try {
                        studentId = Integer.parseInt(rec.student_id);
                    } catch (NumberFormatException e) {
                        // skip invalid id
                        continue;
                    }
                    String status = rec.status != null ? rec.status : "Absent";

                    checkPs.setInt(1, studentId);
                    checkPs.setInt(2, subjectId);
                    checkPs.setString(3, date);
                    checkPs.setString(4, time);

                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next()) {
                            int attendanceId = rs.getInt("attendance_id");
                            String existing = rs.getString("status");
                            if (!existing.equalsIgnoreCase(status)) {
                                updatePs.setString(1, status);
                                updatePs.setString(2, teacherId);
                                updatePs.setInt(3, attendanceId);
                                updatePs.executeUpdate();
                            }
                        } else {
                            insertPs.setInt(1, studentId);
                            insertPs.setString(2, teacherId);
                            insertPs.setInt(3, subjectId);
                            insertPs.setInt(4, streamId);
                            insertPs.setString(5, date);
                            insertPs.setString(6, time);
                            insertPs.setString(7, status);
                            insertPs.executeUpdate();
                        }
                    }
                } // loop

                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }

            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":true}");
        } catch (SQLException ex) {
            ex.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":false,\"message\":\"DB error: " + ex.getMessage().replace("\"","'") + "\"}");
        }
    }
}
