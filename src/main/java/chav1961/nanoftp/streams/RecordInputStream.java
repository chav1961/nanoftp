package chav1961.nanoftp.streams;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * RFC959 part 3.4.1.
 */
public class RecordInputStream extends InputStream {
	private static final int	ESCAPE = 0xFF;
	private static final char[]	LS = System.lineSeparator().toCharArray();

	private final InputStream	nested;
	private final int[]	buffer = new int[3];
	private int			count = 0;
	private int			current = 0;
	
	public RecordInputStream(final InputStream nested) {
		if (nested == null) {
			throw new NullPointerException("Nested input strean can't be null");
		}
		else {
			this.nested = nested;
		}
	}

	@Override
	public int read() throws IOException {
		if (count > 0) {
			final int	val = buffer[current++];
			
			if (current >= count) {
				current = count = 0;
			}
			return val;
		}
		else {
			int	value = nested.read();
			
			if (value == -1) {
				return value;
			}
			else if (value == ESCAPE) {	// Escape
				value = nested.read();
				
				if (value == -1) {
					throw new EOFException("EOF inside escape sequence");
				}
				else if (value == ESCAPE) {
					return value;
				}
				else {
					if ((value & 0b00000001) != 0) {
						for(; count < LS.length; count++) {
							buffer[count] = LS[count];
						}
					}
					if ((value & 0b00000010) != 0) {
						buffer[count++] = -1;
					}
					return read();
				}
			}
			else {
				return value;
			}
		}
	}
}
