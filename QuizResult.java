
import model.DBConnection;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/QuizResult")
public class QuizResult extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Redirect GET to POST for simplicity
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("studentId") == null) {
            response.getWriter().println("<h3>Please login first.</h3>");
            return;
        }
        int studentId = (int) session.getAttribute("studentId");

        int score = 0;
        int totalQuestions = 0;
        try {
            score = Integer.parseInt(request.getParameter("score"));
            totalQuestions = Integer.parseInt(request.getParameter("total"));
        } catch (Exception e) {
            response.getWriter().println("<h3>Invalid score or total parameters.</h3>");
            return;
        }

        double percentage = (totalQuestions > 0) ? (score * 100.0) / totalQuestions : 0;

        String grade;
        if (percentage >= 90) {
            grade = "A+";
        } else if (percentage >= 75) {
            grade = "A";
        } else if (percentage >= 60) {
            grade = "B";
        } else if (percentage >= 50) {
            grade = "C";
        } else {
            grade = "Fail";
        }

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection con = DBConnection.getConnection()) {

            // Save result in DB if not already saved (optional, since Quiz servlet saves)
            // You can skip this if already saved in Quiz servlet
            // Fetch all results for this student
            String selectSql = "SELECT qr.score, qr.submitted_at, s.name AS subject_name, COUNT(q.quiz_id) AS total_questions "
                    + "FROM quiz_results qr "
                    + "LEFT JOIN subjects s ON s.subject_id = qr.quiz_id " + // quiz_id=0 means overall, so subject may be null
                    "LEFT JOIN quizzes q ON q.subject = s.name "
                    + "WHERE qr.student_id = ? "
                    + "GROUP BY qr.id ORDER BY qr.submitted_at DESC";

            PreparedStatement ps2 = con.prepareStatement(selectSql);
            ps2.setInt(1, studentId);
            ResultSet rs = ps2.executeQuery();

            out.println("<!DOCTYPE html>");
            out.println("<html><head><title>Quiz Results</title>");
            out.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>");
            out.println("</head><body class='container py-4'>");

            out.println("<h2 class='mb-4'>Quiz Results</h2>");
            out.println("<p>Welcome, <b>Student ID: " + studentId + "</b></p><hr/>");

            out.println("<h4>Latest Quiz Result</h4>");
            out.println("<p>Score: <b>" + score + "</b> / " + totalQuestions + "</p>");
            out.println("<p>Percentage: <b>" + String.format("%.2f", percentage) + "%</b></p>");
            out.println("<p>Grade: <b>" + grade + "</b></p><hr/>");

            out.println("<h4>Your Quiz History</h4>");
            out.println("<table class='table table-bordered table-striped'>");
            out.println("<thead><tr><th>Date</th><th>Subject</th><th>Score</th><th>Percentage</th></tr></thead>");
            out.println("<tbody>");

            while (rs.next()) {
                Date date = rs.getDate("submitted_at");
                String subject = rs.getString("subject_name");
                if (subject == null) {
                    subject = "General";
                }
                int sc = rs.getInt("score");
                int totalQ = rs.getInt("total_questions");
                double perc = (totalQ > 0) ? (sc * 100.0 / totalQ) : 0;

                out.println("<tr>");
                out.println("<td>" + date + "</td>");
                out.println("<td>" + subject + "</td>");
                out.println("<td>" + sc + " / " + totalQ + "</td>");
                out.println("<td>" + String.format("%.2f", perc) + "%</td>");
                out.println("</tr>");
            }

            out.println("</tbody></table>");
            out.println("<a href='quiz.html' class='btn btn-primary'>Take Another Quiz</a>");
            out.println("</body></html>");

        } catch (Exception e) {
            e.printStackTrace();
            out.println("<h3 class='text-danger'>Error: " + e.getMessage() + "</h3>");
        }
    }
}
