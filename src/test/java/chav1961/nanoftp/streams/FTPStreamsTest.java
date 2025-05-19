package chav1961.nanoftp.streams;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;

import org.junit.Assert;
import org.junit.Test;

public class FTPStreamsTest {
	private static final String[]	RECORDS = {"first","second","third","escape\0377escape"};

	@Test
	public void recordStreamTest() throws IOException {
		try(final ByteArrayOutputStream		baos = new ByteArrayOutputStream()) {
			try(final RecordOutputStream	ros = new RecordOutputStream(baos);
				final PrintStream			ps = new PrintStream(ros)) {
				
				for (String item : RECORDS) {
					ps.println(item);
				}
			}
			
			try{new RecordOutputStream(null).close();
				Assert.fail("Mandatory exception was not detected (null 1-st argument)");
			} catch (NullPointerException exc) {
			}
			
			try(final ByteArrayInputStream	bais = new ByteArrayInputStream(baos.toByteArray());
				final RecordInputStream		ris = new RecordInputStream(bais);
				final Reader				rdr = new InputStreamReader(ris);
				final BufferedReader		brdr = new BufferedReader(rdr)) {
				
				for (String item : RECORDS) {
					Assert.assertEquals(item, brdr.readLine());
				}
			}

			try{new RecordInputStream(null).close();
				Assert.fail("Mandatory exception was not detected (null 1-st argument)");
			} catch (NullPointerException exc) {
			}
		}
	}
}
