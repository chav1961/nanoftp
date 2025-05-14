package chav1961.nanoftp.streams;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class CompressedInputStream extends InputStream {
	private static final int	INITIAL_STATE = 0; 
	private static final int	STATE_DATA = 1; 
	private static final int	STATE_REPLICA = 2; 
	private static final int	STATE_FILLER = 3; 
	
	private final InputStream	nested;
	private final byte			filler;
	private int		state = INITIAL_STATE;
	private int		counter = 0;
	private int		replicator;
	
	public CompressedInputStream(final InputStream nested, final byte filler) {
		if (nested == null) {
			throw new NullPointerException("Nested input stream can't be null");
		}
		else {
			this.nested = nested;
			this.filler = filler;
		}
	}
	
	
	@Override
	public int read() throws IOException {
		int	value;
		
		switch (state) {
			case INITIAL_STATE	:
				value = nested.read();
				
				if ((value & 0b10000000) == 0) {	// Regular string
					state = STATE_DATA;
					counter = value & 0b01111111;
					return read();
				}
				else if ((value & 0b11000000) == 0b10000000) {	// Replicator
					state = STATE_REPLICA;
					counter = value & 0b00111111;
					replicator = mandatoryRead("replicator truncated");
				}
				else if ((value & 0b11000000) == 0b11000000) {	// Filler
					state = STATE_FILLER;
					counter = value & 0b00111111;
					return read();
				}
				else if (value == 0) {	// Escape sequence
					value = mandatoryRead("escape descriptor field truncated");
					if ((value & 0b10000000) != 0) {
						return '\n';
					}
					else if ((value & 0b01000000) != 0) {
						return -1;
					}
					else if ((value & 0b00010000) != 0) {
						final int		len = (mandatoryRead("escape length field truncated") << 8) | mandatoryRead("escape length field truncated");
						final byte[]	marker = new byte[len];
						
						for(int index = 0; index < len; index++) {
							marker[index] = (byte)mandatoryRead("escape restart marker truncated");
						}
						throw new RestartMarkerDetectedException(marker);
					}
					else {
						counter = (mandatoryRead("escape length field truncated") << 8) | mandatoryRead("escape length field truncated"); 
						return read();
					}
				}
				else {
					throw new IOException("Compressed structure corruption detected (unknown prefix)");
				}
			case STATE_DATA		:
				value = mandatoryRead("data block truncated");
				
				if (--counter == 0) {
					state = INITIAL_STATE;
				}
				return value;
			case STATE_REPLICA	:
				if (--counter == 0) {
					state = INITIAL_STATE;
				}
				return replicator;
			case STATE_FILLER	:
				if (--counter == 0) {
					state = INITIAL_STATE;
				}
				return filler & 0xFF;
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
