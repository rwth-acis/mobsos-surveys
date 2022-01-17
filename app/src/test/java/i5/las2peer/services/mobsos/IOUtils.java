package i5.las2peer.services.mobsos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class IOUtils {
	// Returns the contents of the file in a byte array.
	public static synchronized byte[] getBytesFromFile(File file) throws FileNotFoundException, IOException {
		InputStream is = new BufferedInputStream(new FileInputStream(file));

		// Get the size of the file
		long length = file.length();

		// You cannot create an array using a long type.
		// It needs to be an int type.
		// Before converting to an int type, check
		// to ensure that file is not larger than Integer.MAX_VALUE.
		if (length > Integer.MAX_VALUE) {
			// File is too large
		}

		// Create the byte array to hold the data
		byte[] bytes = new byte[(int) length];

		// Read in the bytes
		int offset = 0;
		int numRead = 0;
		while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
			offset += numRead;
		}

		// Ensure all the bytes have been read in
		if (offset < bytes.length) {
			is.close();
			throw new IOException("Could not completely read file " + file.getName());
		}

		// Close the input stream and return bytes
		is.close();
		return bytes;
	}

	public static synchronized String getStringFromFile(File file) throws IOException {
		return new String(getBytesFromFile(file));

	}

	public static Document loadXMLFromString(String filename) throws Exception {
		InputStream ifs = new FileInputStream(filename);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputSource is = new InputSource(new InputStreamReader(ifs, "UTF-8"));
		Document doc = builder.parse(is);
		return doc;
	}

	public static synchronized String getStringFromFile(String filename) {

		try {
			InputStream is = new FileInputStream(filename);

			BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			String s = "";
			String readLine;

			while ((readLine = in.readLine()) != null) {
				s += readLine;
			}
			in.close();
			return s;

		} catch (Exception e) {
			e.printStackTrace();
			return "ERRR";
		}

		/*
		String file = ""; 
		try {
		
		    InputStream is = new FileInputStream(filename);
		    String UTF8 = "utf-8";
		    int BUFFER_SIZE = 8192;
		
		    BufferedReader br = new BufferedReader(new InputStreamReader(is,
		            UTF8), BUFFER_SIZE);
		    String str;
		    while ((str = br.readLine()) != null) {
		        file += str;
		    }
		    return file;
		} catch (Exception e) {
			e.printStackTrace();
			return "ERRR";
		}*/
	}

	public static synchronized void writeBytesToFile(File destination, byte[] bytes)
			throws FileNotFoundException, IOException {

		OutputStream fos = new BufferedOutputStream(new FileOutputStream(destination));
		fos.write(bytes);
		fos.close();
		fos.flush();

	}

	public static String generateFileId(String userlogin) {
		String pattern = "yyyyMMddHHmmssSSS";
		DateFormat df = new SimpleDateFormat(pattern);
		String timestamp = df.format(new Date());
		return userlogin + "_" + timestamp;
	}
}
