package chav1961.nanoftp.streams;

import java.io.IOException;
import java.io.OutputStream;

public class CompressedOutputStream extends OutputStream {
	private static final int	MAX_REPLICA_COUNT = 31;
	private static final int	MAX_FILLER_COUNT = 31;
	
	private final OutputStream	nested;
	private final byte		filler;
	private final byte[]	buffer = new byte[128];
	private int		prevVal = -1;
	private int		displ = 0;
	private int		fillerCount = 0;
	private int		repliCount = 0;
	
	public CompressedOutputStream(final OutputStream nested, final byte filler) {
		if (nested == null) {
			throw new NullPointerException("Nested output stream can't be null");
		}
		else {
			this.nested = nested;
			this.filler = filler;
		}
	}

	@Override
	public void write(final int b) throws IOException {
		final byte	val = (byte)(b & 0xFF);
		
		if (val == filler) {
			if (++fillerCount >= MAX_FILLER_COUNT) {
				nested.write(0b11000000 | (fillerCount & 0x3F));
				fillerCount = 0;
			}
		}
		else if (b == prevVal) {
			if (++repliCount >= MAX_REPLICA_COUNT) {
				nested.write(0b10000000 | (repliCount & 0x3F));
				nested.write(prevVal);
				repliCount = 0;
			}
		}
		else {
			if (fillerCount > 0) {
				nested.write(0b11000000 | (fillerCount & 0x3F));
				fillerCount = 0;
			}
			if (repliCount >= 0) {
				nested.write(0b10000000 | (repliCount & 0x3F));
				nested.write(prevVal);
				repliCount = 0;
			}
			if (displ >= buffer.length) {
				nested.write(displ);
				nested.write(buffer, 0, displ);
				displ = 0;
			}
			buffer[displ++] = val;
			prevVal = b;
		}
	}
	
	@Override
	public void flush() throws IOException {
		if (fillerCount > 0) {
			nested.write(0b11000000 | (fillerCount & 0x3F));
			fillerCount = 0;
		}
		if (repliCount > 0) {
			nested.write(0b10000000 | (repliCount & 0x3F));
			nested.write(prevVal);
			repliCount = 0;
		}
		if (displ > 0) {
			nested.write(displ);
			nested.write(buffer, 0, displ);
			displ = 0;
		}
	}
}
