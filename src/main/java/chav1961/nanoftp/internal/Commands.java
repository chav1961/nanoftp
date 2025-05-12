package chav1961.nanoftp.internal;

import java.util.Locale;

import chav1961.nanoftp.internal.FTPSession.LoggingStatus;
import chav1961.purelib.i18n.interfaces.SupportedLanguages;

public enum Commands {
	USER(false, false, false, false, false, LoggingStatus.NOTLOGGEDIN, "<UserName>", "Type user name to logon"),
	PASS(false, false, false, false, false, LoggingStatus.USERNAMEENTERED, "<Password>", "Type password to logon"),
	ACCT(false, false, false, false, false, null, "<Info>", "Account information"),
	CWD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<NewDirectory>", "Change working directory"),
	XCWD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<NewDirectory>", "Change working directory"),
	CDUP(false, false, false, false, false, LoggingStatus.LOGGEDIN, "", "Change current directory to it's parent"),
	SMNT(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<PathName>", "Mount file system to current session"),
	QUIT(true, false, false, false, false, null, "", "Close connection and quit"),
	REIN(false, false, false, false, false, null, "", "Reset and reinitialize connection"),
	PORT(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<ip0>,<ip1>,<ip2>,<ip3>,<portHi>,<portLo>}", "Enter active mode"),
	PASV(false, false, false, false, false, LoggingStatus.LOGGEDIN, "", "Enter passive mode"),
	TYPE(false, false, false, false, false, LoggingStatus.LOGGEDIN, "{{A|E} [{N|T|A}] | I | L <byteSize>}", "Set transmission content type"),
	STRU(false, false, false, false, false, LoggingStatus.LOGGEDIN, "{F|R|P}", "Define structiure of the file to transfer"),
	MODE(false, false, false, false, false, LoggingStatus.LOGGEDIN, "{S|B|C}", "Set transmission mode"),
	RETR(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<File2Read>", "Read file content"),
	STOR(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<File2Write>", "Write file content"),
	STOU(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<File2Write>", "Write file content with typed or unique name"), // *
	APPE(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<File2Append>", "Append file content"),
	ALLO(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<Space> [R <Space>]", "Try to allocate space for file to store"),
	REST(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<Marker>", "Restore transfer to typed marker"),
	RNFR(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<File2Rename>", "Begin to rename file"),
	RNTO(false, false, false, false, false, LoggingStatus.RENAMESTARTED, "<RenamedFileName>", "End to rename file"),
	ABOR(false, false, false, false, false, LoggingStatus.LOGGEDIN, "", "Cancel file transfer"),
	DELE(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<File2Remove>", "Remove file typed"),
	RMD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<Directory2Remove>", "Remove directory typed"),
	XRMD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<Directory2Remove>", "Remove directory typed"),
	MKD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<NewDirectory>", "Create new directory on the server"),
	XMKD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<NewDirectory>", "Create new directory on the server"),
	PWD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "", "Print current working directory name"),
	XPWD(false, false, false, false, false, LoggingStatus.LOGGEDIN, "", "Print current working directory name"),
	LIST(false, false, false, false, false, LoggingStatus.LOGGEDIN, "[<Directory>]", "List current or typed directory content in Unix 'ls' format"),
	NLST(false, false, false, false, false, LoggingStatus.LOGGEDIN, "[<Directory>]", "List names from current or typed directory"),
	SITE(false, false, false, false, false, LoggingStatus.LOGGEDIN, "<Command> [<parameters>]", "Execute command in the server"),
	SYST(false, false, false, false, false, null, "", "Print OS name"),
	STAT(false, false, false, false, false, null, "[<File>]", "Get status of the server, transmission or file/directory"),
	HELP(false, false, false, false, false, null, "[<CommandAbbr>]", "Print either command list or typed command description"),
	NOOP(false, false, false, false, false, null, "", "No operation. Usually used as 'ping'"),
	AUTH(false, true, false, false, false, null, "<base64-content>", "Authentication/security mechanism"),
	ADAT(false, true, false, false, false, null, "<base64-content>", "Authentication/security data"),
	PROT(false, true, false, false, false, null, "<base64-content>", "Channel protection level"),
	PBSZ(false, true, false, false, false, null, "<base64-content>", "Protection buffer size"),
	CCC(false, true, false, false, false, null, "<base64-content>", "Clear command channel"),
	XCCC(false, true, false, false, false, null, "<base64-content>", "Clear command channel"),
	MIC(false, true, false, false, false, null, "<base64-content>", "Integrity protection command"),
	XMIC(false, true, false, false, false, null, "<base64-content>", "Integrity protection command"),
	CONF(false, true, false, false, false, null, "<base64-content>", "Confidentiality protection command"),
	ENC(false, true, false, false, false, null, "<base64-content>", "Privacy protection command"),
	XENC(false, true, false, false, false, null, "<base64-content>", "Privacy protection command"),
	FEAT(false, false, false, false, false, null, "", "Get list of features for the given FTP server"),
	OPTS(false, false, false, false, false, null, "", "Set options for features on the given FTP server"),
	EPSV(false, false, true, false, false, LoggingStatus.LOGGEDIN, "", "Enter passive mode (possibly IPv6 available)"),
	EPRT(false, false, true, false, false, LoggingStatus.LOGGEDIN, "|{1|2}|{<ipv4>|<ipv6>}|<port>|", "Enter active mode (possibly IPv6 available)"),
	LANG(false, false, false, true, false, LoggingStatus.LOGGEDIN, "<base64-content>", "Language settings", getLangSettings()),
	MDTM(false, false, false, false, true, LoggingStatus.LOGGEDIN, "[<File>]", "Get file modification time"),
	TVFS(false, false, false, false, true, LoggingStatus.LOGGEDIN, "<base64-content>", "File modification time"),
	MLST(false, false, false, false, true, LoggingStatus.LOGGEDIN, "[<File>]", "Describe file properties"),
	MLSD(false, false, false, false, true, LoggingStatus.LOGGEDIN, "[<Dir>]", "Describe directory properties"),
	SIZE(false, false, false, false, true, LoggingStatus.LOGGEDIN, "[<File>]", "Get file size"),
	UTF8(false, false, false, true, false, LoggingStatus.UNKNOWN, "", "Set UTF8 modes"),
	;
	
	private final boolean		exitRequred;
	private final boolean		isRFC2228;
	private final boolean		isRFC2428;
	private final boolean		isRFC2640;
	private final boolean		isRFC3659;
	private final LoggingStatus	context;
	private final String		args;
	private final String		descriptor;
	private final String		featureString;
	
	private Commands(final boolean exitRequired, final boolean isRFC2228, final boolean isRFC2428, final boolean isRFC2640, final boolean isRFC3659, final LoggingStatus context, final String args, final String descriptor) {
		this(exitRequired, isRFC2228, isRFC2428, isRFC2640, isRFC3659, context, args, descriptor, null);
	}
	
	static String getLangSettings() {
		final StringBuilder	sb = new StringBuilder();
		final String		lang = Locale.getDefault().getLanguage();
		
		for (SupportedLanguages item : SupportedLanguages.values()) {
			sb.append(';').append(item.getLocale().getLanguage().toUpperCase());
			if (lang.equalsIgnoreCase(item.getLocale().getLanguage())) {
				sb.append('*');
			}
		}
		return "LANG "+sb.substring(1);
	}

	private Commands(final boolean exitRequired, final boolean isRFC2228, final boolean isRFC2428, final boolean isRFC2640, final boolean isRFC3659, final LoggingStatus context, final String args, final String descriptor, final String featureString) {
		this.exitRequred = exitRequired;
		this.isRFC2228 = isRFC2228;
		this.isRFC2428 = isRFC2428;
		this.isRFC2640 = isRFC2640;
		this.isRFC3659 = isRFC3659;
		this.context = context;
		this.args = args;
		this.descriptor = descriptor;
		this.featureString = featureString == null ? name() : featureString;
	}

	public boolean isExitRequired() {
		return exitRequred;
	}

	public boolean isRFC2228() {
		return isRFC2228;
	}
	
	public boolean isRFC2428() {
		return isRFC2428;
	}
	
	public boolean isRFC2640() {
		return isRFC2640;
	}
	
	public boolean isRFC3659() {
		return isRFC3659;
	}
	
	public boolean isFeature() {
		return isRFC2228 || isRFC2428 || isRFC2640 || isRFC3659;
	}
	
	public LoggingStatus getContext() {
		return context;
	}

	public String getDescriptor() {
		return descriptor;
	}

	public String getArgs() {
		return args;
	}

	public String getFeatureString() {
		return featureString;
	}
}