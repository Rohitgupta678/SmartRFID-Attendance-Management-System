
import model.DBConnection;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/Quiz")
public class Quiz extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("studentId") == null) {
            out.println("<h3>Please login first.</h3>");
            return;
        }
        int studentId = (int) session.getAttribute("studentId");

        String action = request.getParameter("action");

        try (Connection con = DBConnection.getConnection()) {

            if ("load".equals(action)) {
                String subjectIdStr = request.getParameter("subject_id");
                if (subjectIdStr == null || subjectIdStr.isEmpty()) {
                    out.println("<h3>Please select a subject.</h3>");
                    return;
                }
                int subjectId = Integer.parseInt(subjectIdStr);

                // Get subject name from subjects table
                String subjectName = null;
                try (PreparedStatement psSub = con.prepareStatement("SELECT name FROM subjects WHERE subject_id = ?")) {
                    psSub.setInt(1, subjectId);
                    try (ResultSet rsSub = psSub.executeQuery()) {
                        if (rsSub.next()) {
                            subjectName = rsSub.getString("name");
                        } else {
                            out.println("<h3>Invalid subject selected.</h3>");
                            return;
                        }
                    }
                }

                String sql = "SELECT quiz_id, question, option_a, option_b, option_c, option_d FROM quizzes WHERE subject = ?";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, subjectName);
                    try (ResultSet rs = ps.executeQuery()) {

                        out.println("<html><head><title>Quiz - " + subjectName + "</title>");
                        out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
                        out.println("<style>body{background:#f8f9fa;} .card{margin-bottom:1rem;}</style>");
                        out.println("</head><body class='container py-4'>");
                        out.println("<h2 class='text-center mb-4'>Quiz on " + subjectName + "</h2>");
                        out.println("<form action='Quiz' method='post'>");
                        out.println("<input type='hidden' name='action' value='submit'/>");
                        out.println("<input type='hidden' name='subject_id' value='" + subjectId + "'/>");

                        boolean hasQuestions = false;
                        while (rs.next()) {
                            hasQuestions = true;
                            int qid = rs.getInt("quiz_id");
                            out.println("<div class='card shadow-sm p-3'>");
                            out.println("<h5>" + rs.getString("question") + "</h5>");
                            out.println("<div class='form-check'><input class='form-check-input' type='radio' name='q" + qid + "' id='q" + qid + "A' value='A' required>");
                            out.println("<label class='form-check-label' for='q" + qid + "A'>" + rs.getString("option_a") + "</label></div>");
                            out.println("<div class='form-check'><input class='form-check-input' type='radio' name='q" + qid + "' id='q" + qid + "B' value='B'>");
                            out.println("<label class='form-check-label' for='q" + qid + "B'>" + rs.getString("option_b") + "</label></div>");
                            out.println("<div class='form-check'><input class='form-check-input' type='radio' name='q" + qid + "' id='q" + qid + "C' value='C'>");
                            out.println("<label class='form-check-label' for='q" + qid + "C'>" + rs.getString("option_c") + "</label></div>");
                            out.println("<div class='form-check'><input class='form-check-input' type='radio' name='q" + qid + "' id='q" + qid + "D' value='D'>");
                            out.println("<label class='form-check-label' for='q" + qid + "D'>" + rs.getString("option_d") + "</label></div>");
                            out.println("</div>");
                        }

                        if (!hasQuestions) {
                            out.println("<p class='text-center text-danger'>No quiz questions available for this subject.</p>");
                        } else {
                            out.println("<button type='submit' class='btn btn-primary w-100'>Submit Quiz</button>");
                        }
                        out.println("</form>");
                        out.println("</body></html>");
                    }
                }

            } else if ("submit".equals(action)) {
                String subjectIdStr = request.getParameter("subject_id");
                if (subjectIdStr == null) {
                    out.println("<h3>Subject info missing.</h3>");
                    return;
                }
                int subjectId = Integer.parseInt(subjectIdStr);

                String subjectName = null;
                try (PreparedStatement psSub = con.prepareStatement("SELECT name FROM subjects WHERE subject_id = ?")) {
                    psSub.setInt(1, subjectId);
                    try (ResultSet rsSub = psSub.executeQuery()) {
                        if (rsSub.next()) {
                            subjectName = rsSub.getString("name");
                        } else {
                            out.println("<h3>Invalid subject.</h3>");
                            return;
                        }
                    }
                }

                String sql = "SELECT quiz_id, correct_option FROM quizzes WHERE subject = ?";
                int total = 0, correct = 0;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, subjectName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            total++;
                            int qid = rs.getInt("quiz_id");
                            String correctAnswer = rs.getString("correct_option");
                            String userAnswer = request.getParameter("q" + qid);
                            if (userAnswer != null && userAnswer.equalsIgnoreCase(correctAnswer)) {
                                correct++;
                            }
                        }
                    }
                }

                double percentage = (total > 0) ? (correct * 100.0 / total) : 0;

                // Save result for this subject quiz
                String insertSql = "INSERT INTO quiz_results (quiz_id, student_id, score, submitted_at) VALUES (?, ?, ?, NOW())";
                try (PreparedStatement insertPs = con.prepareStatement(insertSql)) {
                    insertPs.setInt(1, 0); // 0 means overall subject quiz
                    insertPs.setInt(2, studentId);
                    insertPs.setInt(3, correct);
                    insertPs.executeUpdate();
                }

                // Redirect to quizResult.html with parameters
                response.sendRedirect("quizResult.html?score=" + correct + "&total=" + total + "&percent=" + String.format("%.2f", percentage));
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println("<h3 class='text-danger'>Error: " + e.getMessage() + "</h3>");
        }
    }
}
