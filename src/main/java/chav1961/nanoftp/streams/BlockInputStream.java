package chav1961.nanoftp.streams;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class BlockInputStream extends InputStream {
	private static final int	INITIAL_STATE = 0; 
	private static final int	STATE_DATA = 1; 
	private static final int	STATE_BUFFER = 2; 
	private final InputStream	nested;
	private final int[]			buffer = new int[3];
	private int		state = INITIAL_STATE;
	private int		count = 0;
	private int		bufferCount = 0;
	
	public BlockInputStream(final InputStream nested) {
		if (nested == null) {
			throw new NullPointerException("Nested input stream can't be null");
		}
		else {
			this.nested = nested;
		}
	}

	@Override
	public int read() throws IOException {
		switch (state) {
			case INITIAL_STATE	:
				final int	value = mandatoryRead("descriptor code truncated");
				
				if ((value & 0b10000000) != 0) {
					buffer[bufferCount++] = '\r';
					buffer[bufferCount++] = '\n';
				}
				if ((value & 0b01000000) != 0) {
					buffer[bufferCount++] = -1;
				}
				if ((value & 0b00010000) != 0) {
					final int 		len = (mandatoryRead("header length field truncated") << 8) | mandatoryRead("header length field truncated");
					final byte[]	marker = new byte[len];
					
					for(int index = 0; index < len; index++) {
						marker[index] = (byte)mandatoryRead("header restart marker truncated");
					}
					throw new RestartMarkerDetectedException(marker);
				}
				count = (mandatoryRead("header length field truncated") << 8) | mandatoryRead("header length field truncated");
				state = STATE_DATA;
				return read();
			case STATE_DATA		: 
				if (count > 0) {
					count--;
					return mandatoryRead("data block truncated");
				}
				else if (bufferCount > 0) {
					state = STATE_BUFFER;
					return read();
				}
				else {
					state = INITIAL_STATE;
					return read();
				}
			case STATE_BUFFER	:
				if (bufferCount > 0) {
					return buffer[--bufferCount];
				}
				else {
					state = INITIAL_STATE;
					return read();
				}
			default :
				throw new UnsupportedOperationException("Automat state ["+state+"] is not supported yet");
		}
	}

	private int mandatoryRead(final String cause) throws IOException {
		final int	value = nested.read();
		
		if (value == -1) {
			throw new EOFException("Unexpected EOF ("+cause+")");
		}
		else {
			return value;
		}
	}
}
