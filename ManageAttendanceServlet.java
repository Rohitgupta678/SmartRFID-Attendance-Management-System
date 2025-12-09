import java.io.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import model.DBConnection;
import javax.servlet.annotation.WebServlet;

@WebServlet("/ManageAttendanceServlet")

public class ManageAttendanceServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // check teacher session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("role") == null
                || !"teacher".equalsIgnoreCase((String) session.getAttribute("role"))) {
            response.sendRedirect("login.html");
            return;
        }

        // fetch subjects and streams to populate dropdowns
        List<Map<String, Object>> subjects = new ArrayList<>();
        List<Map<String, Object>> streams = new ArrayList<>();

        try (Connection con = DBConnection.getConnection()) {

            // subjects: adapt this query to your schema if needed
            try (PreparedStatement ps = con.prepareStatement("SELECT subject_id, name FROM subjects ORDER BY name");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("subject_id", rs.getInt("subject_id"));
                    s.put("name", rs.getString("name"));
                    subjects.add(s);
                }
            }

            // streams table: adapt name/columns if different
            try (PreparedStatement ps = con.prepareStatement("SELECT stream_id, name FROM streams ORDER BY name");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> st = new HashMap<>();
                    st.put("stream_id", rs.getInt("stream_id"));
                    st.put("name", rs.getString("name"));
                    streams.add(st);
                }
            }

        } catch (SQLException ex) {
            throw new ServletException("DB error while loading subjects/streams", ex);
        }

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!doctype html>");
            out.println("<html><head><meta charset='utf-8'>");
            out.println("<meta name='viewport' content='width=device-width, initial-scale=1'>");
            out.println("<title>Manage Attendance</title>");
            out.println("<link href='CSS/bootstrap.min.css' rel='stylesheet'>");
            out.println("<script src='JS/bootstrap.bundle.min.js'></script>");
            out.println("<style>.present{background:#e6ffed;} .absent{background:#ffe6e6;}</style>");
            out.println("</head><body class='bg-light'>");

            out.println("<div class='container mt-4'>");
            out.println("<h2 class='mb-3'>Manage Attendance</h2>");

            // controls
            out.println("<div class='row mb-3'>");
            out.println(" <div class='col-md-4 mb-2'>");
            out.println("  <label class='form-label'>Subject</label>");
            out.println("  <select id='subject' class='form-select'>");
            out.println("   <option value=''>-- Select subject --</option>");
            for (Map<String,Object> s : subjects) {
                out.println("   <option value='" + s.get("subject_id") + "'>" + s.get("name") + "</option>");
            }
            out.println("  </select>");
            out.println(" </div>");

            out.println(" <div class='col-md-4 mb-2'>");
            out.println("  <label class='form-label'>Stream</label>");
            out.println("  <select id='stream' class='form-select'>");
            out.println("   <option value=''>-- Select stream --</option>");
            for (Map<String,Object> st : streams) {
                out.println("   <option value='" + st.get("stream_id") + "'>" + st.get("name") + "</option>");
            }
            out.println("  </select>");
            out.println(" </div>");

            out.println(" <div class='col-md-2 mb-2'>");
            out.println("  <label class='form-label'>Date</label>");
            out.println("  <input id='date' type='date' class='form-control' value='" + java.time.LocalDate.now().toString() + "'>");
            out.println(" </div>");

            out.println(" <div class='col-md-2 mb-2'>");
            out.println("  <label class='form-label'>Time</label>");
            out.println("  <input id='time' type='time' class='form-control' value='" + java.time.LocalTime.now().withSecond(0).withNano(0).toString() + "'>");
            out.println(" </div>");
            out.println("</div>"); // row

            out.println("<div class='mb-3'>");
            out.println("<button id='loadStudents' class='btn btn-outline-primary me-2'>Load Students</button>");
            out.println("<button id='saveAttendance' class='btn btn-success' disabled>Save Attendance</button>");
            out.println("<span id='statusMessage' class='ms-3'></span>");
            out.println("</div>");

            out.println("<div id='studentsArea'>");
            out.println("<!-- Students table will be loaded here -->");
            out.println("</div>");

            out.println("</div>"); // container

            // JS: fetch students & submit attendance
            out.println("<script>");
            out.println("function createTable(students){");
            out.println("  let html = \"<div class='table-responsive'><table class='table table-bordered'>\" +");
            out.println("    \"<thead class='table-light'><tr><th>Roll No</th><th>Name</th><th>RFID</th><th>Status</th></tr></thead><tbody>\";");
            out.println("  students.forEach(s => {");
            out.println("    html += `<tr data-student='${s.student_id}'>` +");
            out.println("            `<td>${s.roll_no || ''}</td>` +");
            out.println("            `<td>${s.name || ''}</td>` +");
            out.println("            `<td>${s.rfid_uid || ''}</td>` +");
            out.println("            `<td>` +");
            out.println("              `<select class='form-select status-select' style='width:160px;'><option value='Present'>Present</option><option value='Absent'>Absent</option></select>` +");
            out.println("            `</td></tr>`;");
            out.println("  });");
            out.println("  html += '</tbody></table></div>'; return html;");
            out.println("}");

            out.println("document.getElementById('loadStudents').addEventListener('click', async function(e){");
            out.println("  const streamId = document.getElementById('stream').value;");
            out.println("  if(!streamId){ alert('Select a stream first'); return; }");
            out.println("  const url = 'GetStudentsServlet?stream_id=' + encodeURIComponent(streamId);");
            out.println("  try {");
            out.println("    const res = await fetch(url);");
            out.println("    if(!res.ok) throw new Error('Server error');");
            out.println("    const students = await res.json();");
            out.println("    if(!students || students.length === 0){ document.getElementById('studentsArea').innerHTML = '<div class=\"alert alert-info\">No students found</div>'; document.getElementById('saveAttendance').disabled = true; return; }");
            out.println("    document.getElementById('studentsArea').innerHTML = createTable(students);");
            out.println("    document.getElementById('saveAttendance').disabled = false;");
            out.println("    // attach change listeners to color rows");
            out.println("    document.querySelectorAll('.status-select').forEach(sel => { sel.addEventListener('change', function(){");
            out.println("      const row = sel.closest('tr'); if(sel.value==='Present'){ row.classList.add('present'); row.classList.remove('absent'); } else { row.classList.add('absent'); row.classList.remove('present'); }");
            out.println("    }); });");
            out.println("  } catch (err) { alert('Error loading students: ' + err.message); }");
            out.println("});");

            out.println("document.getElementById('saveAttendance').addEventListener('click', async function(e){");
            out.println("  const subjectId = document.getElementById('subject').value;");
            out.println("  const streamId = document.getElementById('stream').value;");
            out.println("  const date = document.getElementById('date').value;");
            out.println("  const time = document.getElementById('time').value;");
            out.println("  if(!subjectId){ alert('Select a subject'); return; }");
            out.println("  if(!streamId){ alert('Select a stream'); return; }");
            out.println("  if(!date || !time){ alert('Select date and time'); return; }");
            out.println("  const rows = Array.from(document.querySelectorAll('#studentsArea tbody tr'));");
            out.println("  if(rows.length === 0){ alert('No students to save'); return; }");
            out.println("  const records = rows.map(row => {");
            out.println("    const id = row.getAttribute('data-student');");
            out.println("    const status = row.querySelector('.status-select').value || 'Absent';");
            out.println("    return { student_id: id, status: status };");
            out.println("  });");
            out.println("  const payload = { subject_id: subjectId, stream_id: streamId, date: date, time: time, records: records };");
            out.println("  try {");
            out.println("    const res = await fetch('MarkAttendanceServlet', {");
            out.println("      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)");
            out.println("    });");
            out.println("    const json = await res.json();");
            out.println("    if(res.ok && json.success){");
            out.println("      document.getElementById('statusMessage').innerHTML = '<span class=\"badge bg-success\">Saved</span>'; ");
            out.println("    } else {");
            out.println("      document.getElementById('statusMessage').innerHTML = '<span class=\"badge bg-danger\">Error: ' + (json.message || 'Unknown') + '</span>'; ");
            out.println("    }");
            out.println("  } catch(err){ alert('Save failed: ' + err.message); }");
            out.println("});");

            out.println("</script>");

            out.println("</body></html>");
        }
    }
}
