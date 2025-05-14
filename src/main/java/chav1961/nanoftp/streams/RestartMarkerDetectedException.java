package chav1961.nanoftp.streams;

import java.io.IOException;

public class RestartMarkerDetectedException extends IOException {
	private static final long serialVersionUID = 1188183235548054708L;

	private final byte[]	marker;
	
	public RestartMarkerDetectedException(final byte[] restartMarker) {
		super();
		if (restartMarker == null || restartMarker.length == 0) {
			throw new IllegalArgumentException("Restart marker can be neither null nor empty array");
		}
		else {
			this.marker = restartMarker;
		}
	}

	public byte[] getRestartMarker() {
		return marker.clone();
	}
}
