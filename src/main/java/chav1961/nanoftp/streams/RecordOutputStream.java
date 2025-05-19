package chav1961.nanoftp.streams;

import java.io.IOException;
import java.io.OutputStream;

/**
 * RFC959 part 3.4.1.
 */
public class RecordOutputStream extends OutputStream {
	private static final int	ESCAPE = 0xFF;
	
	private final OutputStream	nested;
	private boolean	closed = false;
	private int		mask = 0;
	
	public RecordOutputStream(final OutputStream nested) {
		if (nested == null) {
			throw new NullPointerException("Nested output stream can't be null");
		}
		else {
			this.nested = nested;
		}
	}

	@Override
	public void write(final int b) throws IOException {
		if (b == 0xFF) {
			nested.write(ESCAPE);
			nested.write(b);
		}
		else if (b == '\r') {
			mask |= 0b00000001;
		}
		else if (b == '\n') {
			mask |= 0b00000001;
		}
		else {
			finish();
			nested.write(b);
		}
	}
	
	@Override
	public void close() throws IOException {
		if (!closed) {
			mask |= 0b00000010;
			finish();
			closed = true;
		}
		super.close();
	}

	public void finish() throws IOException {
		if (mask != 0) {
			nested.write(ESCAPE);
			nested.write(mask);
			mask = 0;
			nested.flush();
		}
	}
}
