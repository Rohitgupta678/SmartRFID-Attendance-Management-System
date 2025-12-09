
import model.DBConnection;

import java.io.*;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/Notes")
@MultipartConfig(maxFileSize = 16177215) // ~16MB
public class Notes extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null || session.getAttribute("role") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String role = (String) session.getAttribute("role");
        String username = (String) session.getAttribute("username");
        String action = request.getParameter("action");
        String streamIdStr = request.getParameter("streamId");
        String subjectIdStr = request.getParameter("subjectId");
        String noteIdStr = request.getParameter("noteId");

        try (Connection con = DBConnection.getConnection()) {
            if (noteIdStr != null) {
                // Download note file
                int noteId = Integer.parseInt(noteIdStr);
                downloadNoteFile(con, noteId, response);
                return;
            }

            if ("teacher".equalsIgnoreCase(role)) {
                handleTeacherGet(request, response, con, username, action, streamIdStr, subjectIdStr);
            } else if ("student".equalsIgnoreCase(role)) {
                handleStudentGet(request, response, con, username, subjectIdStr);
            } else {
                response.getWriter().println("Access denied.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().println("Error: " + e.getMessage());
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null || session.getAttribute("role") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String role = (String) session.getAttribute("role");
        if (!"teacher".equalsIgnoreCase(role)) {
            response.getWriter().println("Only teachers can upload notes.");
            return;
        }

        String username = (String) session.getAttribute("username");
        String action = request.getParameter("action");

        if ("upload".equals(action)) {
            try (Connection con = DBConnection.getConnection()) {
                handleNoteUpload(request, response, con, username);
            } catch (Exception e) {
                e.printStackTrace();
                response.getWriter().println("Error: " + e.getMessage());
            }
        } else {
            response.getWriter().println("Invalid action.");
        }
    }

    private void downloadNoteFile(Connection con, int noteId, HttpServletResponse response) throws Exception {
        String sql = "SELECT filename, file FROM notes WHERE note_id=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, noteId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String filename = rs.getString("filename");
                Blob blob = rs.getBlob("file");
                InputStream inputStream = blob.getBinaryStream();

                response.setContentType(getServletContext().getMimeType(filename));
                response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

                OutputStream out = response.getOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                out.close();
            } else {
                response.getWriter().println("Note not found.");
            }
        }
    }

    private void handleStudentGet(HttpServletRequest request, HttpServletResponse response, Connection con,
            String username, String subjectIdStr) throws Exception {
        // Get student_id
        int studentId = getStudentId(con, username);
        if (studentId == -1) {
            response.getWriter().println("Student not found.");
            return;
        }

        if (subjectIdStr == null) {
            // Show subjects student enrolled in
            String sql = "SELECT sub.subject_id, sub.name FROM subjects sub "
                    + "JOIN student_subject_enrollments sse ON sub.subject_id = sse.subject_id "
                    + "WHERE sse.student_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ResultSet rs = ps.executeQuery();

                PrintWriter out = response.getWriter();
                response.setContentType("text/html");
                out.println("<html><head><title>Subjects</title>");
                out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
                out.println("</head><body class='container py-4'>");
                out.println("<h2>Your Subjects</h2><ul class='list-group'>");

                while (rs.next()) {
                    int subId = rs.getInt("subject_id");
                    String subName = rs.getString("name");
                    out.println("<li class='list-group-item'><a href='Notes?subjectId=" + subId + "'>" + subName + "</a></li>");
                }
                out.println("</ul>");
                out.println("<a href='studentDashboard.html' class='btn btn-secondary mt-3'>Back to Dashboard</a>");
                out.println("</body></html>");
            }
        } else {
            // Show notes for selected subject
            int subjectId = Integer.parseInt(subjectIdStr);
            String sql = "SELECT note_id, title, filename, upload_date FROM notes WHERE subject_id=? ORDER BY upload_date DESC";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, subjectId);
                ResultSet rs = ps.executeQuery();

                PrintWriter out = response.getWriter();
                response.setContentType("text/html");
                out.println("<html><head><title>Notes</title>");
                out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
                out.println("</head><body class='container py-4'>");
                out.println("<h2>Notes for Subject</h2>");
                out.println("<table class='table table-striped'>");
                out.println("<thead><tr><th>Title</th><th>Filename</th><th>Uploaded On</th><th>Download</th></tr></thead><tbody>");

                while (rs.next()) {
                    int noteId = rs.getInt("note_id");
                    String title = rs.getString("title");
                    String filename = rs.getString("filename");
                    Timestamp uploadDate = rs.getTimestamp("upload_date");

                    out.println("<tr>");
                    out.println("<td>" + title + "</td>");
                    out.println("<td>" + filename + "</td>");
                    out.println("<td>" + uploadDate + "</td>");
                    out.println("<td><a href='Notes?noteId=" + noteId + "' class='btn btn-primary btn-sm'>Download</a></td>");
                    out.println("</tr>");
                }
                out.println("</tbody></table>");
                out.println("<a href='Notes' class='btn btn-secondary mt-3'>Back to Subjects</a>");
                out.println("</body></html>");
            }
        }
    }

    private void handleTeacherGet(HttpServletRequest request, HttpServletResponse response, Connection con,
            String username, String action, String streamIdStr, String subjectIdStr) throws Exception {
        int teacherId = getTeacherId(con, username);
        if (teacherId == -1) {
            response.getWriter().println("Teacher not found.");
            return;
        }

        PrintWriter out = response.getWriter();
        response.setContentType("text/html");
        out.println("<html><head><title>Teacher Notes Management</title>");
        out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
        out.println("</head><body class='container py-4'>");
        out.println("<h2>Notes Management</h2>");

        if (streamIdStr == null) {
            // Show streams
            String sql = "SELECT stream_id, name FROM streams ORDER BY name";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                out.println("<h4>Select Stream</h4><ul class='list-group mb-4'>");
                while (rs.next()) {
                    int streamId = rs.getInt("stream_id");
                    String streamName = rs.getString("name");
                    out.println("<li class='list-group-item'><a href='Notes?action=stream&streamId=" + streamId + "'>" + streamName + "</a></li>");
                }
                out.println("</ul>");
            }
        } else if (subjectIdStr == null) {
            // Show subjects for selected stream that teacher teaches
            int streamId = Integer.parseInt(streamIdStr);
            String sql = "SELECT sub.subject_id, sub.name FROM subjects sub "
                    + "JOIN teachers t ON t.subject = sub.name " + // Assuming teacher.subject matches subject.name
                    "WHERE sub.stream_id = ? AND t.teacher_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, streamId);
                ps.setInt(2, teacherId);
                ResultSet rs = ps.executeQuery();

                out.println("<h4>Subjects in Stream</h4><ul class='list-group mb-4'>");
                while (rs.next()) {
                    int subjectId = rs.getInt("subject_id");
                    String subjectName = rs.getString("name");
                    out.println("<li class='list-group-item'><a href='Notes?action=subject&streamId=" + streamId + "&subjectId=" + subjectId + "'>" + subjectName + "</a></li>");
                }
                out.println("</ul>");
                out.println("<a href='Notes' class='btn btn-secondary'>Back to Streams</a>");
            }
        } else {
            // Show upload form and list of uploaded notes for selected subject
            int subjectId = Integer.parseInt(subjectIdStr);

            // Upload form
            out.println("<h4>Upload Note</h4>");
            out.println("<form method='post' action='Notes' enctype='multipart/form-data' class='mb-4'>");
            out.println("<input type='hidden' name='action' value='upload' />");
            out.println("<input type='hidden' name='subjectId' value='" + subjectId + "' />");
            out.println("<div class='mb-3'>");
            out.println("<label for='title' class='form-label'>Title</label>");
            out.println("<input type='text' class='form-control' id='title' name='title' required />");
            out.println("</div>");
            out.println("<div class='mb-3'>");
            out.println("<label for='file' class='form-label'>Select File</label>");
            out.println("<input type='file' class='form-control' id='file' name='file' accept='.pdf,.doc,.docx,.ppt,.pptx,.txt' required />");
            out.println("</div>");
            out.println("<button type='submit' class='btn btn-primary'>Upload</button>");
            out.println("</form>");

            // List uploaded notes
            String sql = "SELECT note_id, title, filename, upload_date FROM notes WHERE subject_id=? AND teacher_id=? ORDER BY upload_date DESC";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, subjectId);
                ps.setInt(2, teacherId);
                ResultSet rs = ps.executeQuery();

                out.println("<h4>Uploaded Notes</h4>");
                out.println("<table class='table table-striped'>");
                out.println("<thead><tr><th>Title</th><th>Filename</th><th>Uploaded On</th><th>Download</th></tr></thead><tbody>");

                while (rs.next()) {
                    int noteId = rs.getInt("note_id");
                    String title = rs.getString("title");
                    String filename = rs.getString("filename");
                    Timestamp uploadDate = rs.getTimestamp("upload_date");

                    out.println("<tr>");
                    out.println("<td>" + title + "</td>");
                    out.println("<td>" + filename + "</td>");
                    out.println("<td>" + uploadDate + "</td>");
                    out.println("<td><a href='Notes?noteId=" + noteId + "' class='btn btn-success btn-sm'>Download</a></td>");
                    out.println("</tr>");
                }
                out.println("</tbody></table>");
            }

            out.println("<a href='Notes?action=stream' class='btn btn-secondary mt-3'>Back to Streams</a>");
        }

        out.println("</body></html>");
    }

    private void handleNoteUpload(HttpServletRequest request, HttpServletResponse response, Connection con, String username) throws Exception {
        int teacherId = getTeacherId(con, username);
        if (teacherId == -1) {
            response.getWriter().println("Teacher not found.");
            return;
        }

        String title = request.getParameter("title");
        String subjectIdStr = request.getParameter("subjectId");
        Part filePart = request.getPart("file");

        if (title == null || title.trim().isEmpty() || subjectIdStr == null || filePart == null || filePart.getSize() == 0) {
            response.getWriter().println("Please fill all fields and select a file.");
            return;
        }

        int subjectId = Integer.parseInt(subjectIdStr);
        InputStream inputStream = filePart.getInputStream();

        String sql = "INSERT INTO notes (title, filename, file, subject_id, teacher_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, filePart.getSubmittedFileName());
            ps.setBlob(3, inputStream);
            ps.setInt(4, subjectId);
            ps.setInt(5, teacherId);
            ps.executeUpdate();
        }

        response.sendRedirect("Notes?action=subject&subjectId=" + subjectId + "&streamId=" + getStreamIdBySubject(con, subjectId));
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

    private int getStreamIdBySubject(Connection con, int subjectId) throws SQLException {
        String sql = "SELECT stream_id FROM subjects WHERE subject_id=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("stream_id");
            }
        }
        return -1;
    }
}
