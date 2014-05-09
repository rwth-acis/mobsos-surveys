package i5.las2peer.services.mobsos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class IOUtils
{
	// Returns the contents of the file in a byte array.
	public static synchronized byte[] getBytesFromFile(File file) throws FileNotFoundException, IOException
	{
		
			InputStream is = new BufferedInputStream(new FileInputStream(file));
			
			// Get the size of the file
			long length = file.length();
			
			// You cannot create an array using a long type.
			// It needs to be an int type.
			// Before converting to an int type, check
			// to ensure that file is not larger than Integer.MAX_VALUE.
			if (length > Integer.MAX_VALUE)
			{
				// File is too large
			}
			
			// Create the byte array to hold the data
			byte[] bytes = new byte[(int)length];
			
			// Read in the bytes
			int offset = 0;
			int numRead = 0;
			while (offset < bytes.length
				   && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0)
			{
				offset += numRead;
			}
			
			// Ensure all the bytes have been read in
			if (offset < bytes.length)
			{
				throw new IOException("Could not completely read file "+file.getName());
			}
			
			// Close the input stream and return bytes
			is.close();
			return bytes;
	}
	
	public static synchronized String getStringFromFile(File file) throws IOException{
		return new String(getBytesFromFile(file));
		
	}
	
	public static synchronized void writeBytesToFile(File destination, byte[] bytes) throws FileNotFoundException, IOException
	{
	
			OutputStream fos = new BufferedOutputStream(new FileOutputStream(destination));
			fos.write(bytes);
			fos.close();
			fos.flush();
		
	}
	
	public static String generateFileId(String userlogin){
		String pattern = "yyyyMMddHHmmssSSS";
		DateFormat df = new SimpleDateFormat(pattern);
		String timestamp = df.format(new Date());
		return userlogin+"_"+timestamp;
	}
}

