import java.io.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import com.google.gson.Gson;
import model.DBConnection;


@WebServlet(urlPatterns = {"/GetStudentsServlet", "/GetStudents"})
public class GetStudentsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String path = req.getServletPath();
        resp.setContentType("application/json;charset=UTF-8");

        try (PrintWriter out = resp.getWriter()) {

            // ✅ CASE 1: /GetStudentsServlet — fetch students by stream_id
            if ("/GetStudentsServlet".equals(path)) {
                String streamParam = req.getParameter("stream_id");
                if (streamParam == null || streamParam.trim().isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("{\"error\": \"Missing stream_id\"}");
                    return;
                }

                int streamId;
                try {
                    streamId = Integer.parseInt(streamParam);
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("{\"error\": \"Invalid stream_id\"}");
                    return;
                }

                List<Map<String, Object>> students = new ArrayList<>();
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(
                             "SELECT student_id, name, roll_no, rfid_uid " +
                             "FROM students WHERE stream_id = ? AND (role = 'student' OR role IS NULL)"
                     )) {
                    ps.setInt(1, streamId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> s = new HashMap<>();
                            s.put("student_id", rs.getInt("student_id"));
                            s.put("name", rs.getString("name"));
                            s.put("roll_no", rs.getString("roll_no"));
                            s.put("rfid_uid", rs.getString("rfid_uid"));
                            students.add(s);
                        }
                    }
                } catch (SQLException ex) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("{\"error\": \"DB error: " + ex.getMessage() + "\"}");
                    return;
                }

                out.write(gson.toJson(students));
                return;
            }

            // ✅ CASE 2: /GetStudents — fetch all students (teacher only)
            if ("/GetStudents".equals(path)) {
                HttpSession session = req.getSession(false);
                if (session == null || session.getAttribute("role") == null
                        || !"teacher".equals(session.getAttribute("role"))) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.write("{\"error\": \"Access denied. Teachers only.\"}");
                    return;
                }

                List<Map<String, Object>> students = new ArrayList<>();
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT u.user_id, u.name, s.roll_no, s.student_id, s.rfid_uid " +
                             "FROM users u JOIN students s ON u.user_id = s.user_id ORDER BY u.name"
                     );
                     ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        Map<String, Object> s = new HashMap<>();
                        s.put("user_id", rs.getInt("user_id"));
                        s.put("student_id", rs.getInt("student_id"));
                        s.put("name", rs.getString("name"));
                        s.put("roll_no", rs.getString("roll_no"));
                        s.put("rfid_uid", rs.getString("rfid_uid"));
                        students.add(s);
                    }
                } catch (SQLException e) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("{\"error\": \"DB error: " + e.getMessage() + "\"}");
                    return;
                }

                out.write(gson.toJson(students));
            }

        }
    }
}
