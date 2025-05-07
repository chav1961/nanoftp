package chav1961.nanoftp.internal;

import java.util.Calendar;

class InternalUtils {
	static String milliseconds2Time(final long milliseconds) {
		final Calendar		cal = Calendar.getInstance();
		
		cal.setTimeInMillis(milliseconds);
		return String.format("%04d%02d%02d%02d%02d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1
				, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
				, cal.get(Calendar.SECOND));
	}
	
}
