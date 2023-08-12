import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Utils {

	public static byte[] myzip(String input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DeflaterOutputStream dos = new DeflaterOutputStream(baos);
		byte[] bytes = input.getBytes("UTF-8"); 
		dos.write(bytes, 0, bytes.length);
		dos.close();
		return baos.toByteArray();
	}	
	
	public static String myunzip(byte[] input) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(input); 
	    InflaterInputStream ini = new InflaterInputStream(bais);
	    ByteArrayOutputStream bout =new ByteArrayOutputStream(512);
	    int b;
	    while ((b = ini.read()) != -1) {
	          bout.write(b);
	    }
	    ini.close();
	    bout.close();
	    return new String(bout.toByteArray());
	}
	
	public static void writeToFile(String filename, String text) {
		PrintStream out = null;
		try {
		    out = new PrintStream(new FileOutputStream(filename));
		    out.print(text);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
		    if (out != null) out.close();
		}		
	}
	
}
