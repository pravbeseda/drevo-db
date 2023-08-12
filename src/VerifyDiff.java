import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.nothome.delta.text.TextPatcher;

/*
 *  Верификация патча
 */
public class VerifyDiff {

	public static void main(String[] args) throws ClassNotFoundException, SQLException, IOException {
		int errorCount = 0;
		if (MakeDB.oldBase == null) {
			System.out.println("oldBase is null... See MakeDB");
			return;
		}
		System.out.println("Start!");		
		Class.forName("org.sqlite.JDBC");
		// Подключаемся к начальной БД
		Connection conn = DriverManager.getConnection("jdbc:sqlite:"+MakeDB.oldBase);
		Statement st = conn.createStatement();
		// Подключаемся к последней БД (патчу)
		Connection conn2 = DriverManager.getConnection("jdbc:sqlite:"+MakeDB.newBase);
		Statement st2 = conn2.createStatement();
		// Выгрузка, с которой сравниваем
		BufferedReader br = new BufferedReader(new FileReader(new File(MakeDB.textBase)));
		// Пробегаемся по текстовой базе с новыми текстами
		String line;
		while ((line = br.readLine()) != null) {
			if (! line.trim().isEmpty()) {
				String[] fields = line.split("<drevo>");
				System.out.print(fields[0].trim());
				String newText = fields[3].trim() + " <drevo> " + fields[4].trim();				
				// Ищем старую версию текста
				ResultSet oldTexts = st.executeQuery("SELECT * FROM tblTexts WHERE _id = '" + fields[0].trim() + "';");
				boolean oldExist = oldTexts.next();
				String oldText = (oldExist) ? Utils.myunzip(oldTexts.getBytes("ziptext")) : "";
				// Ищем патч
				ResultSet patchTexts = st2.executeQuery("SELECT * FROM tblTexts WHERE _id = '" + fields[0].trim() + "';");
				boolean patchExist = patchTexts.next();
				String patchText = (patchExist) ? Utils.myunzip(patchTexts.getBytes("ziptext")) : "";
				if (patchExist && patchTexts.getInt("isfulltext") == 1) { // патч содержит полный текст статьи
					if (patchText.equals(newText)) {
						System.out.print(" fulltext ok ");
					} else {
						System.out.print(" fulltext error ");
						errorCount++;
					}
				} else	if (oldExist) {
					if (patchExist) {
						TextPatcher p = new TextPatcher(oldText);
						String patchedText = p.patch(patchText);
						if (patchedText.equals(newText)) {
							System.out.print(" patched ok ");
						} else {
							System.out.print(" patch error ");
							errorCount++;
						}
					} else { // нет патча
						if (oldText.equals(newText)) {
							System.out.print(" equal text ");
						} else {
							System.out.print(" equal error! ");
							errorCount++;
						}					
					}
				} else {
					System.out.print(" NO patch! NO old text! ");
					errorCount++;				
				}
				System.out.println("  - errors: " + errorCount);
			}
		}
		br.close();
		st.close();
		st2.close();
		conn.close();
		conn2.close();
		System.out.println("Done! Errors: " + errorCount);
	}

}
