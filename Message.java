
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import com.google.gson.Gson;

@WebServlet("/Message")
public class Message extends HttpServlet {

    private final String DB_URL = "jdbc:mysql://localhost:3306/yourdb";
    private final String DB_USER = "root";
    private final String DB_PASS = "password";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public static class MessageData {

        public int message_id;
        public int sender_id;
        public int receiver_id;
        public String message;
        public String timestamp;
        public String sender_name;
        public String receiver_name;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        int userId = (int) session.getAttribute("user_id");
        String role = (String) session.getAttribute("role");

        response.setContentType("application/json;charset=UTF-8");

        try (PrintWriter out = response.getWriter();
                Connection conn = getConnection()) {

            List<MessageData> messages = new ArrayList<>();
            String sql;

            if ("teacher".equals(role)) {
                // Teacher can view all messages they sent or received from students
                sql = "SELECT m.message_id, m.sender_id, m.receiver_id, m.message, m.timestamp, "
                        + "su.name AS sender_name, ru.name AS receiver_name "
                        + "FROM MESSAGES m "
                        + "JOIN USERS su ON m.sender_id = su.user_id "
                        + "JOIN USERS ru ON m.receiver_id = ru.user_id "
                        + "WHERE m.sender_id = ? OR m.receiver_id = ? "
                        + "ORDER BY m.timestamp ASC";
            } else {
                // Students only see messages related to them
                sql = "SELECT m.message_id, m.sender_id, m.receiver_id, m.message, m.timestamp, "
                        + "su.name AS sender_name, ru.name AS receiver_name "
                        + "FROM MESSAGES m "
                        + "JOIN USERS su ON m.sender_id = su.user_id "
                        + "JOIN USERS ru ON m.receiver_id = ru.user_id "
                        + "WHERE m.receiver_id = ? OR m.sender_id = ? "
                        + "ORDER BY m.timestamp ASC";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MessageData msg = new MessageData();
                        msg.message_id = rs.getInt("message_id");
                        msg.sender_id = rs.getInt("sender_id");
                        msg.receiver_id = rs.getInt("receiver_id");
                        msg.message = rs.getString("message");
                        msg.timestamp = rs.getString("timestamp");
                        msg.sender_name = rs.getString("sender_name");
                        msg.receiver_name = rs.getString("receiver_name");
                        messages.add(msg);
                    }
                }
            }

            String json = new Gson().toJson(messages);
            out.print(json);

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null || !"teacher".equals(session.getAttribute("role"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        int senderId = (int) session.getAttribute("user_id");
        String receiverIdStr = request.getParameter("receiver_id");
        String message = request.getParameter("message");

        if (receiverIdStr == null || message == null || message.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try (Connection conn = getConnection()) {
            if ("all".equals(receiverIdStr)) {
                // Send message to all students
                String sql = "SELECT user_id FROM USERS WHERE role='student'";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                        ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int studentId = rs.getInt("user_id");
                        insertMessage(conn, senderId, studentId, message);
                    }
                }
            } else {
                int receiverId = Integer.parseInt(receiverIdStr);
                insertMessage(conn, senderId, receiverId, message);
            }
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void insertMessage(Connection conn, int senderId, int receiverId, String message) throws SQLException {
        String sql = "INSERT INTO MESSAGES (sender_id, receiver_id, message) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setString(3, message);
            ps.executeUpdate();
        }
    }
}
