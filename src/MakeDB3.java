import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.nothome.delta.text.Delta;

// Usage: java MakeDB3 <mode> <mainVersion> <patchVersion>
//   mode: "patch" or "full"
//   patch mode: oldBase = mainBase (creates only patch file)
//   full mode:  oldBase = null (creates both main and patch files)

public class MakeDB3 {
	public final static String pathDB = "./DB/";
	public final static String textBase = pathDB + "drevo.txt";

	public static void main(String[] args) throws ClassNotFoundException, SQLException, IOException {
		if (args.length < 3) {
			System.out.println("Usage: java MakeDB3 <mode> <mainVersion> <patchVersion>");
			System.out.println("  mode: patch | full");
			System.exit(1);
		}

		String mode = args[0];
		String mainVersion = args[1];
		String patchVersion = args[2];

		String mainBase = pathDB + "main." + mainVersion + ".ru.drevoinfo.obb";
		String patchBase = pathDB + "patch." + patchVersion + ".ru.drevoinfo.obb";
		String oldBase;

		if (mode.equals("patch")) {
			oldBase = mainBase;
		} else if (mode.equals("full")) {
			oldBase = null;
		} else {
			System.out.println("Unknown mode: " + mode + ". Use 'patch' or 'full'.");
			System.exit(1);
			return;
		}

		System.out.println("Mode: " + mode);
		System.out.println("mainBase: " + mainBase);
		System.out.println("patchBase: " + patchBase);
		System.out.println("oldBase: " + (oldBase != null ? oldBase : "null (full rebuild)"));
		System.out.println("Start!");
		final int MAXDIFF = 40; // максимальный патч в % от самого текста

		// Создание БД
		new File(patchBase).delete(); // Удаляем старый файл
		Class.forName("org.sqlite.JDBC");
		// Патч
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + patchBase);
		Statement st = conn.createStatement();
		st.execute("CREATE TABLE IF NOT EXISTS tblSlovnik(_id INTEGER, title TEXT PRIMARY KEY ASC)");
		st.execute("CREATE TABLE IF NOT EXISTS tblTopics(article_id INTEGER, topic INTEGER, PRIMARY KEY ([article_id], [topic]))");
		st.execute("CREATE TABLE IF NOT EXISTS tblTexts(_id INTEGER PRIMARY KEY ASC, ziptext BLOB, isfulltext)");

		Connection conn2 = null;
		Statement st2  = null;
		Connection conn3 = null;
		Statement st3  = null;

		if (oldBase != null) {
			// Подключаемся к старой БД для сравнения
			conn3 = DriverManager.getConnection("jdbc:sqlite:"+oldBase);
			st3 = conn3.createStatement();
		} else {
			new File(mainBase).delete(); // Удаляем старый файл
			// Создаем главную таблицу
			conn2 = DriverManager.getConnection("jdbc:sqlite:" + mainBase);
			st2 = conn2.createStatement();
			st2.execute("CREATE TABLE IF NOT EXISTS tblTexts(_id INTEGER PRIMARY KEY, ziptext BLOB, isfulltext)");
			conn2.setAutoCommit(false);
		}

		// Счетчики статистики
		int allCount = 0;
		int patchedTexts = 0;
		int addedTexts = 0;

		// Разбор файла выгрузки и запись в БД
		BufferedReader br = new BufferedReader(new FileReader(new File(textBase)));
		String line;
		PreparedStatement stmt = null;
		conn.setAutoCommit(false);
		while ((line = br.readLine()) != null) {
			if (! line.trim().isEmpty()) {
				allCount++;
				// Разбор строки
				String[] fields = line.split("<drevo>");
				// System.out.println(title + " - " + topics + " - " + line.substring(0, 15));

				// Пишем в БД
				// Заголовок
				stmt = conn.prepareStatement("INSERT INTO tblSlovnik ('_id', 'title') VALUES (?, ?)");
				stmt.setString(1, fields[0].trim());
				if (fields[0].trim().equals("4")) { // Главная страница, добавляем пробел
					stmt.setString(2, " " + fields[1].trim());
				} else {
					stmt.setString(2, fields[1].trim());
				}
				/*// Картинка
				String pic = fields[5].trim();
				if (pic.length() > 0) {
					stmt.setBytes(3, Utils.myzip(pic));
				}*/
				stmt.executeUpdate();
				stmt.close();

				// Темы
				String[] arT = fields[2].trim().split(",");
				boolean isCommon = true;
				for (int i = 0; i < arT.length; i++) {
					if (! arT[i].equals("")) {
						stmt = conn.prepareStatement("INSERT INTO tblTopics ('article_id', 'topic') VALUES (?, ?)");
						stmt.setString(1, fields[0].trim());
						if (fields[0].trim().equals("4")) { // Главная страница
							isCommon = true;
						} else {
							isCommon = (isCommon && ! (Integer.parseInt(arT[i]) >= 13));
						}
						stmt.setString(2, arT[i]);
						stmt.executeUpdate();
						stmt.close();
					}
				}
				if (isCommon) {
					stmt = conn.prepareStatement("INSERT INTO tblTopics ('article_id', 'topic') VALUES (?, ?)");
					stmt.setString(1, fields[0].trim());
					stmt.setString(2, "99");
					stmt.executeUpdate();
					stmt.close();
				}
				System.out.print(fields[0] + " - " + fields[2]);

				// Обрабатываем оглавление
				String content = fields[3].trim();
				// content = content.replace("> ", ">").replace(" <", "<").replace("<a href=\"", "");
				content = content.replace("<div class=\"indent\"> ","#").replace("</div>", "");
				content = content.replace("<a href=\"", "").replace("\">", "|").replace("</a>", "");
				content = content.replace("##", "#_").replace("   ", " ").replace("  ", " ");
				//Utils.writeToFile(fields[0]+".tmp", content);

				// Пишем текст
				String newText =  content + " <drevo> " + fields[4].trim();

				if (oldBase != null) {
					stmt = conn.prepareStatement("INSERT INTO tblTexts ('_id', 'ziptext', 'isfulltext') VALUES (?, ?, ?)");
				} else {
					stmt = conn2.prepareStatement("INSERT INTO tblTexts ('_id', 'ziptext', 'isfulltext') VALUES (?, ?, ?)");
				}

				stmt.setString(1, fields[0]);
				boolean needAddition = true;
				if (st3 != null) {
					// ищем старую версию текста
					ResultSet oldTexts = st3.executeQuery("SELECT * FROM tblTexts WHERE _id = '" + fields[0] + "';");
					if (oldTexts.next()) { // текст найден
						String oldText = Utils.myunzip(oldTexts.getBytes("ziptext"));
						if (oldText.equals(newText)) { // тексты равны
							needAddition = false;
							System.out.print(" -  equals! ");
						} else {
							Delta d = new Delta();
							String patch = d.compute(oldText, newText);
							int percent = Math.round(100 * patch.length() / newText.length());
							System.out.print(" - " + percent + "% ");
							if (percent < MAXDIFF) { // пишем патч
								stmt.setBytes(2, Utils.myzip(patch));
								stmt.setInt(3, 0);
								stmt.executeUpdate();
								needAddition = false;
								System.out.print(" - patch ");
								patchedTexts++;
							}
						}
					}
				}
				if (needAddition) { // просто добавляем текст
					stmt.setBytes(2, Utils.myzip(newText));
					stmt.setInt(3, 1);
					stmt.executeUpdate();
					System.out.print(" - add ");
					addedTexts++;
				}
				stmt.close();
				System.out.println(" -  done");
			}
		}

		br.close();

		System.out.println("Creating indexes...");
		st.execute("CREATE INDEX IF NOT EXISTS id ON tblSlovnik(_id)");
		st.execute("CREATE INDEX IF NOT EXISTS title ON tblSlovnik(title)");
		try {
			System.out.println("Commit patch...");
			conn.commit();
		} finally {
			conn.setAutoCommit(true);
			System.out.println("Vacuum patch...");
			st.execute("VACUUM");
			st.close();
			conn.close();
		}
		if (conn2 != null) {
			try {
				System.out.println("Commit main...");
				conn2.commit();
			} finally {
				conn2.setAutoCommit(true);
				System.out.println("Vacuum main...");
				st2.execute("VACUUM");
				st2.close();
				conn2.close();
			}
		}
		if (conn3 != null) {
			st3.close();
			conn3.close();
		}
		System.out.println("Count: " + allCount + " Added: " + addedTexts + " (" + Math.round(100 * addedTexts / allCount) + "%) Patched: " + patchedTexts+ " (" + Math.round(100 * patchedTexts / allCount) + "%)");
		System.out.println("Done!");
	}

}
