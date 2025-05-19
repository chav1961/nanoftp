package chav1961.nanoftp.streams;

import java.io.IOException;
import java.io.OutputStream;

public class BlockOutputStream extends OutputStream {
	private final OutputStream	nested;

	public BlockOutputStream(final OutputStream nested) {
		if (nested == null) {
			throw new NullPointerException("Nested stream can't be null");
		}
		else {
			this.nested = nested;
		}
	}

	@Override
	public void write(int b) throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	public void writeRestartMarker(final byte[] marker) throws IOException {
		if (marker == null || marker.length == 0) {
			throw new IllegalArgumentException("Restart marker can't be neither null nor empty array");
		}
		else {
			writeBlock();
			nested.write(0b00001000);
			nested.write(0);
			nested.write(marker.length);
			nested.write(marker);
		}
	}
	
	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		super.close();
	}

	private void writeBlock() {
		// TODO Auto-generated method stub
		
	}

}
