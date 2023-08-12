import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.nothome.delta.text.Delta;

public class MyDiff {
	public final static String pathDB = "./DB/";
	public final static String oldBase = pathDB + "main.24.ru.drevoinfo.obb";
	public final static String newBase = pathDB + "main.29.ru.drevoinfo.obb";
	public final static String patchBase = pathDB + "patch.30.ru.drevoinfo.obb";

	public static void main(String[] args) throws ClassNotFoundException, SQLException, IOException {
		
		PreparedStatement stmt = null;

		System.out.println("Start!");
		Class.forName("org.sqlite.JDBC");
		// Подключаемся к начальной БД
		Connection conn = DriverManager.getConnection("jdbc:sqlite:"+oldBase);
		Statement st = conn.createStatement();
		// Подключаемся к последней БД
		Connection conn2 = DriverManager.getConnection("jdbc:sqlite:"+newBase);
		Statement st2 = conn2.createStatement();
		// Создаем дифференциальную БД
		File f = new File(patchBase);
		f.delete();
		Connection conn3 = DriverManager.getConnection("jdbc:sqlite:"+patchBase);
		Statement st3 = conn3.createStatement();
		st3.execute("CREATE TABLE IF NOT EXISTS tblDrevo(_id INTEGER, title TEXT, topic INTEGER)");
		st3.execute("CREATE TABLE IF NOT EXISTS tblDiffs(_id INTEGER, zipdiff BLOB, isfulltext)");
		// Перебираем все тексты из свежей выгрузки
		ResultSet newTexts = st2.executeQuery("SELECT * FROM tblTexts;");
		while (newTexts.next()) {
			System.out.print(newTexts.getString("_id"));
			// Ищем старую версию текста
			ResultSet oldTexts = st.executeQuery("SELECT * FROM tblTexts WHERE _id = '" + newTexts.getString("_id") + "';");
			stmt = conn3.prepareStatement("INSERT INTO tblDiffs ('_id', 'zipdiff', 'isfulltext') VALUES (?, ?, ?)");
			stmt.setString(1, newTexts.getString("_id"));
			if (oldTexts.next()) {
				// найден старый текст, вычисляем diff
				String oldText = Utils.myunzip(oldTexts.getBytes("ziptext"));
				String newText = Utils.myunzip(newTexts.getBytes("ziptext"));
				if (! oldText.equals(newText)) {
					Delta d = new Delta();
					String patch = d.compute(oldText, newText);
					byte[] zippatch = Utils.myzip(patch);
					stmt.setBytes(2, zippatch);				
					stmt.setInt(3, 0);
					int percent = Math.round(100 * zippatch.length / newTexts.getBytes("ziptext").length); 
					System.out.print(" - " + zippatch.length + " " + percent + "%");
					stmt.executeUpdate();
					if (percent > 80) {
						Utils.writeToFile(newTexts.getString("_id")+"-" + percent + ".test", patch);					
						//writeToFile(newTexts.getString("_id")+"-2.test", myunzip(zippatch));						
					}
				} else {
					System.out.print(" not change ");
				}
			} else {
				// в старой БД эта статья отсутствует
				// поэтому копируем текст полностью
				stmt.setBytes(2, newTexts.getBytes("ziptext"));
				stmt.setInt(3, 1);
				stmt.executeUpdate();
				System.out.print(" new text ");
			}
			stmt.close();
			oldTexts.close();
			System.out.println(" - done!");
		}
		newTexts.close();
		
		ResultSet rs = st2.executeQuery("SELECT * FROM tblDrevo;");
		while (rs.next()) {
			// копируем заголовки
			stmt = conn3.prepareStatement("INSERT INTO tblDrevo ('_id', 'title', 'topic') VALUES (?, ?, ?)");
			stmt.setString(1, rs.getString("_id"));
			stmt.setString(2, rs.getString("title"));
			stmt.setString(3, rs.getString("topic"));
			stmt.executeUpdate();
			stmt.close();
			System.out.println(rs.getString("_id") + " - title done!");
		}
		rs.close();
		
		System.out.println("Creating indexes...");
		
		st3.execute("CREATE INDEX IF NOT EXISTS id ON tblDrevo(_id)");		
		st3.execute("CREATE INDEX IF NOT EXISTS title ON tblDrevo(title)");
		st3.execute("CREATE INDEX IF NOT EXISTS title2 ON tblDrevo(title COLLATE NOCASE)");
		st3.execute("CREATE INDEX IF NOT EXISTS topic ON tblDrevo(topic,title)");
		st3.execute("CREATE INDEX IF NOT EXISTS topic2 ON tblDrevo(topic, title COLLATE NOCASE)");

		System.out.println("Done!");
		st.close();
		st2.close();
		st3.close();
		conn.close();
		conn2.close();
		conn3.close();
	}
	
}
