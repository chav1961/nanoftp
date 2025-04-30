package chav1961.nanoftp.internal;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class SimpleValidator {
	private final Map<String, char[]>	users = new HashMap<>();

	public SimpleValidator(final String user, final String password) {
		users.put(user.toUpperCase(), password.toCharArray());
	}
	
	public SimpleValidator(final File root) {
		final File[]	content = root.listFiles((File f)->f.isDirectory());
		
		if (content != null) {
			for(File item : content) {
				users.put(item.getName().toUpperCase(), item.getName().toCharArray());
			}
		}
	}

	public boolean isUserExists(final String user) {
		return users.containsKey(user.toUpperCase());
	}
	
	public boolean areCredentialsValid(final String user, final char[] password) {
		return isUserExists(user) && Arrays.compare(users.get(user.toUpperCase()), password) == 0;
	}
}
