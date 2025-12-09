
import model.DBConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/Syllabus")
public class Syllabus extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null || session.getAttribute("role") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String username = (String) session.getAttribute("username");
        String role = (String) session.getAttribute("role");
        String action = request.getParameter("action");
        String subjectIdStr = request.getParameter("subjectId");
        String syllabusIdStr = request.getParameter("syllabusId");

        try (Connection con = DBConnection.getConnection()) {
            if ("markCompleted".equalsIgnoreCase(action) && "teacher".equalsIgnoreCase(role)) {
                // Teacher marks topic completed
                int syllabusId = Integer.parseInt(syllabusIdStr);
                int teacherId = getTeacherId(con, username);
                markTopicCompleted(con, syllabusId, teacherId);
                response.sendRedirect("Syllabus?subjectId=" + request.getParameter("subjectId"));
                return;
            }

            if (subjectIdStr == null) {
                // Show subjects list for syllabus
                showSubjectsList(out, con, role);
            } else {
                int subjectId = Integer.parseInt(subjectIdStr);
                if ("teacher".equalsIgnoreCase(role)) {
                    showTeacherSyllabus(out, con, subjectId, username);
                } else {
                    showStudentSyllabus(out, con, subjectId, username);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.println("<div class='alert alert-danger'>Error: " + e.getMessage() + "</div>");
        }
    }

    private void showSubjectsList(PrintWriter out, Connection con, String role) throws SQLException {
        out.println(htmlHeader("Syllabus - Subjects"));
        out.println("<div class='container py-4'>");
        out.println("<h2 class='mb-4'>Select Subject</h2>");
        out.println("<div class='list-group'>");

        String sql = "SELECT subject_id, name FROM subjects ORDER BY name";
        try (PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int subjectId = rs.getInt("subject_id");
                String name = rs.getString("name");
                out.println("<a href='Syllabus?subjectId=" + subjectId + "' class='list-group-item list-group-item-action'>" + name + "</a>");
            }
        }

        out.println("</div></div>");
        out.println(htmlFooter());
    }

    private void showStudentSyllabus(PrintWriter out, Connection con, int subjectId, String username) throws SQLException {
        int studentId = getStudentId(con, username);
        if (studentId == -1) {
            out.println("<div class='alert alert-danger'>Student not found.</div>");
            return;
        }

        out.println(htmlHeader("Syllabus - Student View"));
        out.println("<div class='container py-4'>");
        out.println("<h2 class='mb-4'>Syllabus Progress</h2>");

        // Get syllabus with completed topics by any teacher (for simplicity)
        String sql = "SELECT s.syllabus_id, s.unit_number, s.topic_number, s.topic_title, s.description, "
                + "CASE WHEN sp.progress_id IS NOT NULL THEN 1 ELSE 0 END AS completed "
                + "FROM syllabus s LEFT JOIN syllabus_progress sp ON s.syllabus_id = sp.syllabus_id "
                + "WHERE s.subject_id = ? "
                + "GROUP BY s.syllabus_id, s.unit_number, s.topic_number, s.topic_title, s.description, completed "
                + "ORDER BY s.unit_number, s.topic_number";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ResultSet rs = ps.executeQuery();

            int currentUnit = -1;
            out.println("<div class='accordion' id='syllabusAccordion'>");

            while (rs.next()) {
                int unit = rs.getInt("unit_number");
                if (unit != currentUnit) {
                    if (currentUnit != -1) {
                        out.println("</div></div>"); // close previous unit collapse and card-body
                    }
                    currentUnit = unit;
                    out.println("<div class='accordion-item'>");
                    out.println("<h2 class='accordion-header' id='headingUnit" + unit + "'>");
                    out.println("<button class='accordion-button collapsed' type='button' data-bs-toggle='collapse' data-bs-target='#collapseUnit" + unit + "' aria-expanded='false' aria-controls='collapseUnit" + unit + "'>");
                    out.println("Unit " + unit);
                    out.println("</button></h2>");
                    out.println("<div id='collapseUnit" + unit + "' class='accordion-collapse collapse' aria-labelledby='headingUnit" + unit + "' data-bs-parent='#syllabusAccordion'>");
                    out.println("<div class='accordion-body'>");
                }

                int syllabusId = rs.getInt("syllabus_id");
                String topicTitle = rs.getString("topic_title");
                String description = rs.getString("description");
                boolean completed = rs.getInt("completed") == 1;

                out.println("<div class='d-flex justify-content-between align-items-center mb-2'>");
                out.println("<div><strong>" + topicTitle + "</strong><br/><small>" + description + "</small></div>");
                if (completed) {
                    out.println("<span class='badge bg-primary' title='Completed'>&#10003;</span>");
                } else {
                    out.println("<span class='badge bg-secondary' title='Not Completed'>&#10007;</span>");
                }
                out.println("</div>");
            }
            if (currentUnit != -1) {
                out.println("</div></div></div>"); // close last unit
            }
            out.println("</div>"); // close accordion
        }

        out.println("<a href='Syllabus' class='btn btn-secondary mt-4'>Back to Subjects</a>");
        out.println("</div>");
        out.println(htmlFooter());
    }

    private void showTeacherSyllabus(PrintWriter out, Connection con, int subjectId, String username) throws SQLException {
        int teacherId = getTeacherId(con, username);
        if (teacherId == -1) {
            out.println("<div class='alert alert-danger'>Teacher not found.</div>");
            return;
        }

        out.println(htmlHeader("Syllabus - Teacher View"));
        out.println("<div class='container py-4'>");
        out.println("<h2 class='mb-4'>Syllabus Progress</h2>");

        String sql = "SELECT s.syllabus_id, s.unit_number, s.topic_number, s.topic_title, s.description, "
                + "CASE WHEN sp.progress_id IS NOT NULL THEN 1 ELSE 0 END AS completed "
                + "FROM syllabus s LEFT JOIN syllabus_progress sp ON s.syllabus_id = sp.syllabus_id AND sp.teacher_id = ? "
                + "WHERE s.subject_id = ? "
                + "ORDER BY s.unit_number, s.topic_number";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            ps.setInt(2, subjectId);
            ResultSet rs = ps.executeQuery();

            int currentUnit = -1;
            out.println("<div class='accordion' id='syllabusAccordion'>");

            while (rs.next()) {
                int unit = rs.getInt("unit_number");
                if (unit != currentUnit) {
                    if (currentUnit != -1) {
                        out.println("</div></div>"); // close previous unit collapse and card-body
                    }
                    currentUnit = unit;
                    out.println("<div class='accordion-item'>");
                    out.println("<h2 class='accordion-header' id='headingUnit" + unit + "'>");
                    out.println("<button class='accordion-button collapsed' type='button' data-bs-toggle='collapse' data-bs-target='#collapseUnit" + unit + "' aria-expanded='false' aria-controls='collapseUnit" + unit + "'>");
                    out.println("Unit " + unit);
                    out.println("</button></h2>");
                    out.println("<div id='collapseUnit" + unit + "' class='accordion-collapse collapse' aria-labelledby='headingUnit" + unit + "' data-bs-parent='#syllabusAccordion'>");
                    out.println("<div class='accordion-body'>");
                }

                int syllabusId = rs.getInt("syllabus_id");
                String topicTitle = rs.getString("topic_title");
                String description = rs.getString("description");
                boolean completed = rs.getInt("completed") == 1;

                out.println("<form method='post' action='Syllabus' class='d-flex justify-content-between align-items-center mb-2'>");
                out.println("<div><strong>" + topicTitle + "</strong><br/><small>" + description + "</small></div>");
                out.println("<input type='hidden' name='syllabusId' value='" + syllabusId + "'/>");
                out.println("<input type='hidden' name='subjectId' value='" + subjectId + "'/>");
                out.println("<button type='submit' name='action' value='markCompleted' class='btn btn-sm " + (completed ? "btn-success" : "btn-outline-secondary") + "' " + (completed ? "disabled" : "") + ">");
                out.println(completed ? "Completed &#10003;" : "Mark Completed");
                out.println("</button>");
                out.println("</form>");
            }
            if (currentUnit != -1) {
                out.println("</div></div></div>"); // close last unit
            }
            out.println("</div>"); // close accordion
        }

        out.println("<a href='Syllabus' class='btn btn-secondary mt-4'>Back to Subjects</a>");
        out.println("</div>");
        out.println(htmlFooter());
    }

    private void markTopicCompleted(Connection con, int syllabusId, int teacherId) throws SQLException {
        String checkSql = "SELECT progress_id FROM syllabus_progress WHERE syllabus_id = ? AND teacher_id = ?";
        try (PreparedStatement ps = con.prepareStatement(checkSql)) {
            ps.setInt(1, syllabusId);
            ps.setInt(2, teacherId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                // Already marked completed, do nothing or update date if you want
                return;
            }
        }

        String insertSql = "INSERT INTO syllabus_progress (syllabus_id, teacher_id, completed_date) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(insertSql)) {
            ps.setInt(1, syllabusId);
            ps.setInt(2, teacherId);
            ps.setDate(3, Date.valueOf(LocalDate.now()));
            ps.executeUpdate();
        }
    }

    private int getTeacherId(Connection con, String username) throws SQLException {
        String sql = "SELECT t.teacher_id FROM teachers t JOIN users u ON t.user_id = u.user_id WHERE u.username=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("teacher_id");
            }
        }
        return -1;
    }

    private int getStudentId(Connection con, String username) throws SQLException {
        String sql = "SELECT s.student_id FROM students s JOIN users u ON s.user_id = u.user_id WHERE u.username=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("student_id");
            }
        }
        return -1;
    }

    private String htmlHeader(String title) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'/>"
                + "<title>" + title + "</title>"
                + "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'/>"
                + "</head><body>"
                + "<nav class='navbar navbar-expand-lg navbar-dark bg-primary mb-4'>"
                + "<div class='container'><a class='navbar-brand' href='Syllabus'>Syllabus Tracker</a></div></nav>";
    }

    private String htmlFooter() {
        return "<script src='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js'></script>"
                + "</body></html>";
    }
}
