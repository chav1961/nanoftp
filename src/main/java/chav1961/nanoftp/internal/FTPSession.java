package chav1961.nanoftp.internal;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import chav1961.purelib.basic.Utils;
import chav1961.purelib.basic.interfaces.LoggerFacade;
import chav1961.purelib.basic.interfaces.LoggerFacade.Severity;
import chav1961.purelib.basic.interfaces.LoggerFacadeOwner;
import chav1961.purelib.basic.interfaces.ProgressIndicator;

class FTPSession implements Runnable, LoggerFacadeOwner {
	private static final File[]	EMPTY_FILE_ARRAY = new File[0];

	private static enum LoggingStatus {
		NOTLOGGEDIN,
		USERNAMEENTERED,
		LOGGEDIN
	}
	
	private static enum Commands {
		USER(false, false, LoggingStatus.NOTLOGGEDIN, "<UserName>", "Type user name to logon"),
		PASS(false, false, LoggingStatus.USERNAMEENTERED, "<Password>", "Type password to logon"),
		ACCT(false, false, null, "<Info>", "Account information"),
		CWD(false, false, LoggingStatus.LOGGEDIN, "<NewDirectory>", "Change working directory"),
		CDUP(false, false, LoggingStatus.LOGGEDIN, "", "Change current directory to it's parent"),
		SMNT(false, false, LoggingStatus.LOGGEDIN, "<PathName>", "Mount file system to current session"),
  		QUIT(true, false, null, "", "Close connection and quit"),
		REIN(false, false, null, "", "Reset and reinitialize connection"),
		PORT(false, false, LoggingStatus.LOGGEDIN, "<ip0>,<ip1>,<ip2>,<ip3>,<portHi>,<portLo>}", "Enter active mode"),
		PASV(false, false, LoggingStatus.LOGGEDIN, "", "Enter passive mode"),
  		TYPE(false, false, LoggingStatus.LOGGEDIN, "{{A|E} [{N|T|A}] | I | L <byteSize>}", "Set transmission content type"),
		STRU(false, false, LoggingStatus.LOGGEDIN, "{F|R|P}", "Define structiure of the file to transfer"),
  		MODE(false, false, LoggingStatus.LOGGEDIN, "{S|B|C}", "Set transmission mode"),
  		RETR(false, false, LoggingStatus.LOGGEDIN, "<File2Read>", "Read file content"),
  		STOR(false, false, LoggingStatus.LOGGEDIN, "<File2Write>", "Write file content"),
  		STOU(false, false, LoggingStatus.LOGGEDIN, "<File2Write>", "Write file content with typed or unique name"), // *
  		APPE(false, false, LoggingStatus.LOGGEDIN, "<File2Append>", "Append file content"),
  		ALLO(false, false, LoggingStatus.LOGGEDIN, "<Space> [R <Space>]", "Try to allocate space for file to store"),
  		REST(false, false, LoggingStatus.LOGGEDIN, "<Marker>", "Restore transfer to typed marker"),
  		RNFR(false, false, LoggingStatus.LOGGEDIN, "<File2Rename>", "Begin to rename file"),
  		RNTO(false, false, LoggingStatus.LOGGEDIN, "<RenamedFileName>", "End to rename file"),
  		ABOR(false, false, LoggingStatus.LOGGEDIN, "", "Cancel file transfer"),
  		DELE(false, false, LoggingStatus.LOGGEDIN, "<File2Remove>", "Remove file typed"),
  		RMD(false, false, LoggingStatus.LOGGEDIN, "<Directory2Remove>", "Remove directory typed"),
  		XRMD(false, false, LoggingStatus.LOGGEDIN, "<Directory2Remove>", "Remove directory typed"),
  		MKD(false, false, LoggingStatus.LOGGEDIN, "<NewDirectory>", "Create new directory on the server"),
		XMKD(false, false, LoggingStatus.LOGGEDIN, "<NewDirectory>", "Create new directory on the server"),
		PWD(false, false, LoggingStatus.LOGGEDIN, "", "Print current working directory name"),
  		XPWD(false, false, LoggingStatus.LOGGEDIN, "", "Print current working directory name"),
		LIST(false, false, LoggingStatus.LOGGEDIN, "[<Directory>]", "List current or typed directory content in Unix 'ls' format"),
  		NLST(false, false, LoggingStatus.LOGGEDIN, "[<Directory>]", "List names from current or typed directory"),
  		SITE(false, false, LoggingStatus.LOGGEDIN, "<Command> [<parameters>]", "Execute command in the server"),
		SYST(false, false, null, "", "Print OS name"),
  		STAT(false, false, null, "[<File>]", "Get status of the server, transmission or file/directory"),
  		HELP(false, false, null, "[<CommandAbbr>]", "Print either command list or typed command description"),
		NOOP(false, false, null, "", "No operation. Usually used as 'ping'"),
		FEAT(false, false, null, "", "Get list of features for the given FTP server"),
  		EPSV(false, false, LoggingStatus.LOGGEDIN, "", "Enter passive mode (possibly IPv6 available)"),
  		EPRT(false, false, LoggingStatus.LOGGEDIN, "|{1|2}|{<ipv4>|<ipv6>}|<port>|", "Enter active mode (possibly IPv6 available)"),
  	// Extended commands		
  		SIZE(false, true, LoggingStatus.LOGGEDIN, "", ""),
  		;
		
		private final boolean		exitRequred;
		private final boolean		isFeature;
		private final LoggingStatus	context;
		private final String		args;
		private final String		descriptor;
		
		private Commands(final boolean exitRequired, final boolean isFeature, final LoggingStatus context, final String args, final String descriptor) {
			this.exitRequred = exitRequired;
			this.isFeature = isFeature;
			this.context = context;
			this.args = args;
			this.descriptor = descriptor;
		}

		public boolean isExitRequired() {
			return exitRequred;
		}

		public boolean isFeature() {
			return isFeature;
		}
		
		public LoggingStatus getContext() {
			return context;
		}
		
		public String getArgs() {
			return args;
		}
		
		public String getDescriptor() {
			return descriptor;
		}
	}	
	
	private static enum MessageType {
		MSG_OPEN_CONN_FOR_LIST(125, " Opening ASCII mode data connection for file list.\r\n"),
		MSG_OPEN_BIN_CONN_FOR_FILE(150, " Opening binary mode data connection for file %1$s\r\n"),
		MSG_OPEN_ASCII_CONN_FOR_FILE(150, " Opening ASCII mode data connection for file %1$s\r\n"),
		MSG_COMMAND_OK(200, " Command OK\r\n"),
		MSG_COMMAND_IGNORED(202, " Command recognized but ignored.\r\n"),
		MSG_SYSTEM_STATUS(211, " System status OK\r\n"),
		MSG_EXTENSIONS_START(211, "-Extensions supported:\r\n"),
		MSG_EXTENSIONS_END(211, " END\r\n"),
		MSG_COMMANDS_START(211, "-Commands supported:\r\n"),
		MSG_COMMANDS_END(211, " END\r\n"),
		MSG_COMMANDS_HELP(211, " Command: %1$s %2$s - %3$s\r\n"),
		MSG_COMMANDS_HELP_MISSING(211, " Command %1$s is not supported\r\n"),
		MSG_TRANSFER_STATUS(213, " File transfer status: transferring %1$d bytes, error bit is %1$b\r\n"),
		MSG_FILE_SIZE(213, " %1$d\r\n"),
		MSG_SYSTEM(215, " %1$s\r\n"),
		MSG_WELCOME(220, " Welcome to the nano FTP-Server\r\n"),
		MSG_CONNECTION_RESET(220, " Connection reset. Type 'USER' or 'ACCT' command to connect\r\n"),
		MSG_CLOSING_CONN(221, " Closing connection\r\n"),
		MSG_NO_TRANSFER_IN_PROGRESS(225, " No any transfer in progress, command ignored\r\n"),
		MSG_TRANSFER_COMPLETED(226, " Transfer completed\r\n"),
		MSG_TRANSFER_COMPLETED_DETAILED(226, " Transfer completed, %1$d bytes transmitted, avg speed is %2$.3f bytes/sec, file name is \"%3$s\"\r\n"),
		MSG_ENTERING_PASSIVE_MODE(227, " Entering Passive Mode (%1$s,%2$s,%3$s,%4$s,%5$d,%6$d)\r\n"),
		MSG_ENTERING_EXTENDED_PASSIVE_MODE(229, " Entering Extended Passive Mode (|||%1$d|)\r\n"),
		MSG_WELCOME_USER_LOGGED(230, "-Welcome to server\r\n"),
		MSG_USER_LOGGED(230, " User logged in successfully\r\n"),
		MSG_DIRECTORY_CREATED(250, " Directory %1$s successfully created\r\n"),	  
		MSG_DIRECTORY_CHANGED(250, " The current directory has been changed to %1$s\r\n"),
		MSG_DIRECTORY_REMOVED(250, " Directory %1$s successfully removed\r\n"),	  
		MSG_FILE_REMOVED(250, " File %1$s successfully removed\r\n"),	  
		MSG_CURRENT_DIR(257, " \"%1$s\"\r\n"),	  
		MSG_USER_NAME_OK(331, " User name okay, need password\r\n"),
		MSG_AWAITING_CONTINUATION(350, " Requested file action pending further information.\r\n"),
		MSG_STILL_RUNNING(421, " Service is still running and can't process new request.\r\n"),
		MSG_NO_DATA_CONNECTION(425, " No data connection was established\r\n"),
		MSG_ABORT_DATA_CONNECTION(426, " Transfer errors detected, connection closed\r\n"),
		MSG_UNKNOWN_COMMAND(500, " Unknown command\r\n"),
		MSG_ILLEGAL_ARGUMENT(501, " Illegal argument [%1$s]\r\n"),
		MSG_MISSING_FILE_NAME(501, " File name missing\r\n"),
		MSG_MISSING_RNFR_BEFORE_RNTO(503, " RNTO command without RNFR preceding\r\n"),
		MSG_UNSUPPORTED_ARGUMENT(504, " Argument [%1$s] is not supported by the server\r\n"),
		MSG_TRANSFER_MODE_NOT_SET(504, " Transfer mode is not set yet\r\n"),
		MSG_USER_ALREADY_LOGGED(530," User already logged in\r\n"),
		MSG_USER_NOT_LOGGED(530," Command in wrong context (possibly not logged in)\r\n"),
		MSG_USER_NOT_ENTERED(530," User name is not entered yet\r\n"),
		MSG_FAILURE_FILE_UNAVAILABLE(550, " Requested action not taken. File %1$s unavailable.\r\n"),
		MSG_FAILURE_FILE_NOT_EXISTS(550, " Entity %1$s does not exist, not a file or is not available for current user\r\n"),
		MSG_FAILURE_DIRECTORY_NOT_EXISTS(550, " Entity %1$s does not exist, not a directory or is not available for current user\r\n"),
		MSG_FAILURE_FILE_ALREADY_EXISTS(550, " File %1$s already exist\r\n"),
		MSG_FAILURE_DIRECTORY_NOT_CREATED(550, " Failed to create new directory %1$s\r\n");
		;
		  
		private final int		code;
		private final String	message;
		  
		private MessageType(final int code, final String message) {
			this.code = code;
			this.message = message;
		}
		  
		public int getCode() {	
			return code;
		}
		  
		public String getMessage() {
			return message;
		}
	}
  
	private static enum ConnectionMode {
		ACTIVE,
		PASSIVE,
		NONE
	}

	private static enum TransferType {
		ASCII,
		BINARY,
		UNKNOWN
	}

	@FunctionalInterface
	private static interface Sender {
		void send(String content) throws IOException;
	}
	
	private final Socket 			controlSocket;
	private final int				dataPort;
	private final ExecutorService	service;
	private final LoggerFacade		logger;
	private final File 				root;
	private final DataConnection	conn = new DataConnection();
	private final boolean 			debugMode;
	private final SimpleValidator	validator;

	private String 				currDirectory;
	private Writer 				controlOutWriter;
	private TransferType 		transferMode;
	private LoggingStatus 		currentLoggingStatus;
	private String				currentUser;
	private File				oldFile;
	private DataCopier			copier = null;
	private Future<?>			future = null;
  
	FTPSession(final Socket client, final int dataPort, final ExecutorService service, final LoggerFacade logger, final File root, final SimpleValidator validator, final boolean debugMode) {
	    this.controlSocket = client;
	    this.dataPort = dataPort;
	    this.service = service;
	    this.logger = logger;
	    this.validator = validator;
	    this.debugMode = debugMode;
	    this.root = root;
	    clearSettings();
	}

	@Override
	public LoggerFacade getLogger() {
		return logger;
	}
  
	@Override
	public void run() {
		debug("FTP session started, current working directory is <" + this.currDirectory + ">");

		try(final BufferedReader	controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
			final Writer			controlOutWriter = new OutputStreamWriter(controlSocket.getOutputStream())) {
			String	line;

			this.controlOutWriter = controlOutWriter;
			sendAnswer(MessageType.MSG_WELCOME);
			while ((line = controlIn.readLine()) != null) {
				if (!executeCommand(line)) {
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				controlSocket.close();
				debug("FTP session ended");
			} catch (IOException e) {
				debug("Could not close socket");
			}
		}
	}

	private boolean executeCommand(final String c) throws IOException {
		final int 		blank = c.indexOf(' ');
		final String 	command = blank == -1 ? c : c.substring(0, blank);
		final String 	args = blank == -1 ? "" : c.substring(blank + 1).trim();
		  
		debug("Command: " + command + ", args: <" + args + ">");
		try {
			final Commands	cmd = Commands.valueOf(command.trim().toUpperCase());
	
			if (cmd.getContext() != null && cmd.getContext() != currentLoggingStatus) {
				sendAnswer(MessageType.MSG_USER_NOT_LOGGED);
				return true;
			}
			else {
				try {
					switch (cmd) {
						case ABOR:
					  		handleAbor();
							break;
						case ACCT:
					  		handleAcct();
							break;
						case ALLO:
					  		handleAllo(args);
							break;
						case APPE:
					  		handleStor(args, true, false);
							break;
						case CDUP:
					  		handleCwd("..");
							break;
						case CWD:
					  		handleCwd(args);
							break;
						case DELE:
					  		handleDele(args);
							break;
						case EPRT:
					  		handleEPort(args);
							break;
						case EPSV:
					  		handleEpsv();
							break;
						case FEAT:
					  		handleFeat();
							break;
						case HELP:
					  		handleHelp(args);
							break;
						case LIST:
					  		handleList(args);
							break;
						case MKD:
					  		handleMkd(args);
							break;
						case MODE:
					  		handleMode(args.toUpperCase());
							break;
						case NLST:
					  		handleNlst(args);
							break;
						case NOOP:
					  		handleNoop();
							break;
						case PASS:
					  		handlePass(args);
							break;
						case PASV:
					  		handlePasv();
							break;
						case PORT:
					  		handlePort(args);
							break;
						case PWD:
					  		handlePwd();
							break;
						case QUIT:
					  		handleQuit();
							break;
						case REIN:
					  		handleRein();
							break;
						case REST:
					  		handleRest(args);
							break;
						case RETR:
					  		handleRetr(args);
							break;
						case RMD:
					  		handleRmd(args);
							break;
						case RNFR:
					  		handleRnfr(args);
							break;
						case RNTO:
					  		handleRnto(args);
							break;
						case SITE:
					  		handleSite();
							break;
						case SIZE:
					  		handleSize(args);
							break;
						case SMNT:
					  		handleSmnt();
							break;
						case STAT:
					  		handleStat(args);
							break;
						case STOR:
					  		handleStor(args, false, false);
							break;
						case STOU:
					  		handleStor(args, false, true);
							break;
						case STRU:
					  		handleStru(args.toUpperCase());
							break;
						case SYST:
					  		handleSyst();
							break;
						case TYPE:
					  		handleType(args.toUpperCase());
							break;
						case USER:
					  		handleUser(args);
							break;
						case XMKD:
					  		handleMkd(args);
							break;
						case XPWD:
					  		handlePwd();
							break;
						case XRMD:
					  		handleRmd(args);
							break;
						default:
					  		throw new UnsupportedOperationException("Command ["+c+"] is not supported yet");
					}
					if (cmd != Commands.RNFR) {
						oldFile = null;
					}
					return !cmd.isExitRequired();
				} catch (IllegalArgumentException exc) {
					sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT);
					return true;
				}
			}
		} catch (IllegalArgumentException exc) {
			sendAnswer(MessageType.MSG_UNKNOWN_COMMAND);
			return true;
		}
	}

	private void handleRein() throws IOException {
		clearSettings();
		sendAnswer(MessageType.MSG_CONNECTION_RESET);
	}  

	private void handleUser(final String userName) throws IOException {
		if (Utils.checkEmptyOrNullString(userName)) {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, userName);
		}
		else {
			switch (currentLoggingStatus) {
				case LOGGEDIN		:
					sendAnswer(MessageType.MSG_USER_ALREADY_LOGGED);
					break;
				case USERNAMEENTERED:
				case NOTLOGGEDIN	:
					if (validator.isUserExists(userName)) {
						sendAnswer(MessageType.MSG_USER_NAME_OK);
						currentUser = userName;
						currentLoggingStatus = LoggingStatus.USERNAMEENTERED;
					} else {
						sendAnswer(MessageType.MSG_USER_NOT_LOGGED);
					}
					break;
				default:
					throw new UnsupportedOperationException("Logging status ["+currentLoggingStatus+"] is not supported yet");
			}
		}
	}

	private void handlePass(final String password) throws IOException {
		if (Utils.checkEmptyOrNullString(password)) {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, password);
		}
		else {
			switch (currentLoggingStatus) {
				case LOGGEDIN		:
					sendAnswer(MessageType.MSG_USER_ALREADY_LOGGED);
					break;
				case NOTLOGGEDIN	:
					sendAnswer(MessageType.MSG_USER_NOT_ENTERED);
					break;
				case USERNAMEENTERED:
					if (validator.areCredentialsValid(currentUser, password.toCharArray())) {
						currentLoggingStatus = LoggingStatus.LOGGEDIN;
						sendAnswer(MessageType.MSG_WELCOME_USER_LOGGED);
						sendAnswer(MessageType.MSG_USER_LOGGED);
					}
					else {
						sendAnswer(MessageType.MSG_USER_NOT_LOGGED);
					}
					break;
				default:
					throw new UnsupportedOperationException("Logging status ["+currentLoggingStatus+"] is not supported yet");
			}
		}
	}

	private void handleCwd(final String args) throws IOException {
		final File	current = getFileDesc(args);
	  
		if (current.exists() && current.isDirectory()) {
			currDirectory = getFileName(current);
			sendAnswer(MessageType.MSG_DIRECTORY_CHANGED, currDirectory);
		} else {
			debug("Not found: <"+current.getAbsolutePath()+">");
			sendAnswer(MessageType.MSG_FAILURE_FILE_UNAVAILABLE, getFileName(current));
		}
	}

	private void handleStat(final String args) throws IOException {
		if (Utils.checkEmptyOrNullString(args)) {
			if (future == null || future.isDone()) {
				sendAnswer(MessageType.MSG_SYSTEM_STATUS);
			}
			else {
				final DataCopier temp = copier;
				sendAnswer(MessageType.MSG_TRANSFER_STATUS, temp.processed, temp.error);
			}
		}
		else {
			final File	current = getFileDesc(args);
			final File[] 	dirContent = getDirContent(current);
		
			if (dirContent == null) {
				sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS, getFileName(current));
			} else {
				sendDirContent(dirContent, this::sendCommandLine);
			}
		}
	}
  
	// https://cr.yp.to/ftp/list/binls.html
	private void handleList(final String args) throws IOException {
		if (!conn.isConnectionValid()) {
			sendAnswer(MessageType.MSG_NO_DATA_CONNECTION);
		} 
		else {
			final File		current = getFileDesc(args == null || args.startsWith("-") ? "" : args);
			final File[] 	dirContent = getDirContent(current);
		
			if (dirContent == null) {
				sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS, getFileName(current));
			} else {
				sendDirContent(dirContent, this::sendDataLine);
				closeDataConnection();
			}
		}
	}

	private void handleNlst(final String args) throws IOException {
		if (!conn.isConnectionValid()) {
			sendAnswer(MessageType.MSG_NO_DATA_CONNECTION);
		} 
		else if (isFileNameValid(args)) {
			final File	current = getFileDesc(args == null ? "" : args);
			final File[] 	dirContent = getDirContent(current);
		
			if (dirContent == null) {
				sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS, getFileName(current));
			} else {
				sendAnswer(MessageType.MSG_OPEN_CONN_FOR_LIST);

				for (File content : dirContent) {
					sendDataLine(content.getName());
				}

				sendAnswer(MessageType.MSG_TRANSFER_COMPLETED);
				closeDataConnection();
			}
		}
		else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, args);
		}
	}

	private void handlePwd() throws IOException {
		sendAnswer(MessageType.MSG_CURRENT_DIR, getFileName(getFileDesc(currDirectory)));
	}

	private void handlePort(final String args) throws IOException {
		// args ip,ip,ip,ip,port/256,port%256
		if (args.matches("\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3}")) {
			final String[] 	content = args.split(",");
			final String 	hostName = content[0].trim() + '.' + content[1].trim() + '.' + content[2].trim() + '.' + content[3].trim();
			final int 		port = Integer.parseInt(content[4].trim()) * 256 + Integer.parseInt(content[5].trim());
		
			openDataConnectionActive(hostName, port);
			sendAnswer(MessageType.MSG_COMMAND_OK);
		}
		else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, args);
		}
	}

	private void handleEPort(final String args) throws IOException {
		// args either |2|::1|12345| or |1|192.168.0.1|12345|
		if (args.matches("\\|(1|2)\\|.*\\|\\d{1,5}\\|")) {
			final String[] splitArgs = args.split("\\|");
			
			openDataConnectionActive(splitArgs[2], Integer.parseInt(splitArgs[3]));
			sendAnswer(MessageType.MSG_COMMAND_OK);
		}
		else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, args);
		}
	}

	private void handlePasv() throws IOException {
		final SocketAddress	addr = openDataConnectionPassive(0);
	    final String	host = ((InetSocketAddress)addr).getAddress().getHostAddress();
	    final int		port = ((InetSocketAddress)addr).getPort();;
	    final String[]	ip = host.split("\\."); 
	    final int 		p1 = port / 256;
	    final int 		p2 = port % 256;
	    
	    sendAnswer(MessageType.MSG_ENTERING_PASSIVE_MODE, ip[0], ip[1], ip[2], ip[3], p1, p2);
	    waitDataConnectionPassive(port);
	}

	private void handleEpsv() throws IOException {
		final SocketAddress	addr = openDataConnectionPassive(0);
		final int	 port = ((InetSocketAddress)addr).getPort();
	  
		sendAnswer(MessageType.MSG_ENTERING_EXTENDED_PASSIVE_MODE, port);
		waitDataConnectionPassive(port);
	}

	private void handleQuit() throws IOException {
		sendAnswer(MessageType.MSG_CLOSING_CONN);
	}

	private void handleSyst() throws IOException {
		sendAnswer(MessageType.MSG_SYSTEM, System.getProperty("os.name"));
	}

	private void handleFeat() throws IOException {
		sendAnswer(MessageType.MSG_EXTENSIONS_START);
		for(Commands item : Commands.values()) {
			if (item.isFeature()) {
				sendCommandLine(' ' + item.name() + '\r' + '\n');
			}
		}
		sendAnswer(MessageType.MSG_EXTENSIONS_END);
	}

	private void handleMkd(final String args) throws IOException {
		if (isFileNameValid(args)) {
			final File dir = getFileDesc(args);

			if (!dir.mkdir()) {
				sendAnswer(MessageType.MSG_FAILURE_DIRECTORY_NOT_CREATED);
				debug("Failed to create new directory");
			}
			else {
				sendAnswer(MessageType.MSG_DIRECTORY_CREATED, getFileName(dir));
			}
		}
		else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, args);
		}
	}

	private void handleRmd(final String dir) throws IOException {
		if (isFileNameValid(dir)) {
			final File d = getFileDesc(dir);

			if (d.exists() && d.isDirectory()) {
				if (d.delete()) {
					sendAnswer(MessageType.MSG_DIRECTORY_REMOVED, getFileName(d));
				}
				else {
					sendAnswer(MessageType.MSG_FAILURE_FILE_UNAVAILABLE, getFileName(d));
				}
			} 
			else {
				sendAnswer(MessageType.MSG_FAILURE_DIRECTORY_NOT_EXISTS, getFileName(d));
			}
		} else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, dir);
		}
	}

	private void handleDele(final String file) throws IOException {
		if (isFileNameValid(file)) {
			File f = getFileDesc(file);

			if (f.exists() && f.isFile()) {
				if (f.delete()) {
					sendAnswer(MessageType.MSG_FILE_REMOVED, getFileName(f));
				}
				else {
					sendAnswer(MessageType.MSG_FAILURE_FILE_UNAVAILABLE, getFileName(f));
				}
			} 
			else {
				sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS, getFileName(f));
			}
		} else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, file);
		}
	}

	private void handleSite() throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_IGNORED);
	}
  
	private void handleType(final String mode) throws IOException {
		if (mode.matches("((A|E|I)(\\s+(N|T|C)){0,1}|L\\s+\\d+)")) {
			final String[]	modes = mode.split("\\s+");
		  
			switch (modes[0].charAt(0)) {
				case 'A' :   
					if (modes.length > 1) {
			  			if (modes[1].charAt(0) != 'N') {
					  		sendAnswer(MessageType.MSG_UNSUPPORTED_ARGUMENT, mode);
			  			}
			  			else {
					  		transferMode = TransferType.ASCII;
							sendAnswer(MessageType.MSG_COMMAND_OK);
			  			}
					}
					else {
				  		transferMode = TransferType.ASCII;
						sendAnswer(MessageType.MSG_COMMAND_OK);
					}
					break;
				case 'I' :   
			  		transferMode = TransferType.BINARY;
			  		sendAnswer(MessageType.MSG_COMMAND_OK);
					break;
				case 'L' : 
	  				if (Integer.parseInt(modes[1]) != 8) {
				  		sendAnswer(MessageType.MSG_UNSUPPORTED_ARGUMENT, mode);
	  				}
	  				else {
	  			  		transferMode = TransferType.BINARY;
	  			  		sendAnswer(MessageType.MSG_COMMAND_OK);
	  				}
			  		break;
			  	case 'E' : 
			  		sendAnswer(MessageType.MSG_UNSUPPORTED_ARGUMENT, mode);
			  	default :
			  		sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, mode);
			}
		}
		else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, mode);
		}
	}

	private void handleMode(final String mode) throws IOException {
		if (Utils.checkEmptyOrNullString(mode) || !mode.matches("(S|B|C)")) {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, mode);
		}
		else if ("S".equals(mode)) {
			sendAnswer(MessageType.MSG_COMMAND_OK);
		}
		else {
			sendAnswer(MessageType.MSG_UNSUPPORTED_ARGUMENT, mode);
		}
	}
  
	private void handleRetr(final String file) throws IOException {
		if (isFileNameValid(file)) {
			final File f = getFileDesc(file);
	
			if (!f.exists() || !f.isFile() || !f.canRead()) {
				sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS);
			}
			else if (future != null && !future.isDone()) {
				sendAnswer(MessageType.MSG_STILL_RUNNING);
			}
			else {
				copier = null;
				future = null;
				switch (transferMode) {
				  	case ASCII:
				        sendAnswer(MessageType.MSG_OPEN_ASCII_CONN_FOR_FILE, f.getName());
				
				        debug("Starting file transmission of " + f.getName() + " in ASCII mode");
				        copier = new DataCopier(f, new OutputStreamWriter(conn.getOutputStream()));
				        future = startTransmission(copier);
						break;
					case BINARY:
				        sendAnswer(MessageType.MSG_OPEN_BIN_CONN_FOR_FILE, f.getName());
				
				        debug("Starting file transmission of " + f.getName() + " in BINARY mode");
				        copier = new DataCopier(f, conn.getOutputStream());
				        future = startTransmission(copier);
						break;
					case UNKNOWN :
						sendAnswer(MessageType.MSG_TRANSFER_MODE_NOT_SET);
						break;
					default :
						throw new UnsupportedOperationException("Transafer mode ["+transferMode+"] is not supporte yet");
				}
			}
		}
		else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, file);
		}
	}

	private void handleAbor() throws IOException {
		if (future == null || future.isDone()) {
			sendAnswer(MessageType.MSG_NO_TRANSFER_IN_PROGRESS);
		}
		else {
			future.cancel(true);
			sendAnswer(MessageType.MSG_TRANSFER_COMPLETED);
		}
	}  
	
	private void handleAcct() throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_IGNORED);
	}  
	
	private void handleAllo(final String size) throws IOException {
		if (Utils.checkEmptyOrNullString(size) || !size.matches("\\d+(\\s+R\\s+\\d+){0,1}")) {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, size);
		}
		else {
			sendAnswer(MessageType.MSG_COMMAND_IGNORED);
		}
	}  
  
	private void handleStor(final String file, final boolean append, final boolean createUnique) throws IOException {
		if (isFileNameValid(file)) {
			if (future != null && !future.isDone()) {
				sendAnswer(MessageType.MSG_STILL_RUNNING);
			}
			else {
				final File 	f, temp = getFileDesc(file);

				if (temp.exists() && temp.isFile() && createUnique) {
					f = File.createTempFile(temp.getAbsolutePath()+".", "");
				}
				else {
					f = temp;
				}
				switch (transferMode) {
					case ASCII		:
						sendAnswer(MessageType.MSG_OPEN_ASCII_CONN_FOR_FILE, f.getName());
					
			            debug("Start receiving file " + f.getName() + " in ASCII mode");
				        copier = new DataCopier(new InputStreamReader(conn.getInputStream()), f, append);
				        future = startTransmission(copier);
		            	break;						
					case BINARY		:
			            sendAnswer(MessageType.MSG_OPEN_BIN_CONN_FOR_FILE, f.getName());
		
			            debug("Start receiving file " + f.getName() + " in BINARY mode");
				        copier = new DataCopier(conn.getInputStream(), f, append);
				        future = startTransmission(copier);
			            break;
					case UNKNOWN	:
			  			sendAnswer(MessageType.MSG_TRANSFER_MODE_NOT_SET);
			  			break;
			  		default :
			  			throw new UnsupportedOperationException("Transafer mode ["+transferMode+"] is not supporte yet");
				}
			}
		}
		else {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, file);
		}
	}

	private void handleRest(final String displ) throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_IGNORED);
	}  

	private void handleRnfr(final String file) throws IOException {
		final File	f = getFileDesc(file);
	  
		if (f.exists() && f.isFile()) {
			oldFile = f;
			sendAnswer(MessageType.MSG_AWAITING_CONTINUATION);
		}
		else {
			sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS);
		}
	}  

	private void handleRnto(final String file) throws IOException {
		final File	f = getFileDesc(file);
	  
		if (f.exists()) {
			sendAnswer(MessageType.MSG_FAILURE_FILE_ALREADY_EXISTS, getFileName(f));
		}
		else if (oldFile == null) {
			sendAnswer(MessageType.MSG_MISSING_RNFR_BEFORE_RNTO);
		}
		else {
			if (oldFile.renameTo(f)) {
				sendAnswer(MessageType.MSG_COMMAND_OK);
			}
			else {
				sendAnswer(MessageType.MSG_FAILURE_FILE_UNAVAILABLE, getFileName(f));
			}
		}
	}  

	private void handleNoop() throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_OK);
	}  

	private void handleHelp(final String name) throws IOException {
		if (Utils.checkEmptyOrNullString(name)) {
			sendAnswer(MessageType.MSG_COMMANDS_START);
			for(Commands item : Commands.values()) {
				if (!item.isFeature()) {
					sendCommandLine(' ' + item.name() + ' ' + item.getArgs() + '\r' + '\n');
				}
				else {
					sendCommandLine(' ' + item.name() + ' ' + item.getArgs() + " (feature)\r\n");
				}
			}
			sendAnswer(MessageType.MSG_COMMANDS_END);
		}
		else {
			try {
				final Commands	c = Commands.valueOf(name.trim().toUpperCase());
			  
				sendAnswer(MessageType.MSG_COMMANDS_HELP, c.name(), c.getArgs(), c.getDescriptor());
			} catch (IllegalArgumentException exc) {
				sendAnswer(MessageType.MSG_COMMANDS_HELP_MISSING, name);
			}
		}
	}  

	private void handleStru(final String parm) throws IOException {
		if (Utils.checkEmptyOrNullString(parm) || !parm.matches("(F|R|P)")) {
			sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, parm);
		}
		else if ("F".equals(parm)) {
			sendAnswer(MessageType.MSG_COMMAND_OK);
		}
		else {
			sendAnswer(MessageType.MSG_UNSUPPORTED_ARGUMENT, parm);
		}
	}  

	private void handleSmnt() throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_IGNORED);
	}  
	
	private void handleSize(final String file) throws IOException {
		final File	f = getFileDesc(file);
	  
		if (f.exists() && f.isFile()) {
			sendAnswer(MessageType.MSG_FILE_SIZE, f.length());
		}
		else {
			sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS);
		}
	}  
  
	private void debug(final String msg) {
		if (debugMode) {
			getLogger().message(Severity.debug, "Thread " + Thread.currentThread().getName() + ": " + msg);
		}
	}

	private synchronized void sendAnswerSilent(final MessageType msg, final Object... parameters) {
		try {
			sendAnswer(msg, parameters);
		} catch (IOException e) {
			debug("Send error: "+e.getLocalizedMessage());
		}
	}
	
	private synchronized void sendAnswer(final MessageType msg, final Object... parameters) throws IOException {
		final String	result = msg.getCode()+msg.getMessage().formatted(parameters);
      
		sendCommandLine(result);
		controlOutWriter.flush();
	}

	private synchronized void sendCommandLine(final String line) throws IOException {
		debug("Answer: "+line);
		controlOutWriter.write(line);
	}  

	private void sendDataLine(final String msg) throws IOException {
		if (!conn.isConnectionValid()) {
			debug("Cannot send message, because no data connection is established");
			sendAnswer(MessageType.MSG_NO_DATA_CONNECTION);
		} else {
			final Writer	wr = conn.getWriter();
        
			debug("Data: "+msg);
			wr.write(msg);
			wr.write('\r');
			wr.write('\n');
		}
	}

	private void sendDirContent(final File[] dirContent, final Sender sender) throws IOException {
		sendAnswer(MessageType.MSG_OPEN_CONN_FOR_LIST);

		for (File content : dirContent) {
			final Path		path = content.toPath();
			final Set<PosixFilePermission> 	permissions = getFilePermissions(content);
			final Calendar	cal = Calendar.getInstance();
		  
			cal.setTimeInMillis(content.lastModified());					  
			sender.send("%1$c%2$c%3$c%4$c%5$c%6$c%7$c%8$c%9$c%10$c 1 %11$s %12$s %13$13d %14$3s %15$3d %16$02d:%17$02d %18$s".formatted(
				  	content.isDirectory() ? 'd' : '-',
				  	permissions.contains(PosixFilePermission.OWNER_READ) ? 'r' : '-',
				  	permissions.contains(PosixFilePermission.OWNER_WRITE) ? 'w' : '-',
				  	permissions.contains(PosixFilePermission.OWNER_EXECUTE) ? 'x' : '-',
				  	permissions.contains(PosixFilePermission.GROUP_READ) ? 'r' : '-',
				  	permissions.contains(PosixFilePermission.GROUP_WRITE) ? 'w' : '-',
				  	permissions.contains(PosixFilePermission.GROUP_EXECUTE) ? 'x' : '-',
				  	permissions.contains(PosixFilePermission.OTHERS_READ) ? 'r' : '-',
				  	permissions.contains(PosixFilePermission.OTHERS_WRITE) ? 'w' : '-',
				  	permissions.contains(PosixFilePermission.OTHERS_EXECUTE) ? 'x' : '-',
				  	Files.getOwner(path).getName(),
				  	getFileGroup(content),
				  	content.length(),
				  	cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH),
				  	cal.get(Calendar.DAY_OF_MONTH),
				  	cal.get(Calendar.HOUR_OF_DAY),
				  	cal.get(Calendar.MINUTE),
				  	content.getName()
				  ));
		}
		sendAnswer(MessageType.MSG_TRANSFER_COMPLETED);
	}
  
	private void clearSettings() {
		this.currDirectory = "/";
		this.transferMode = TransferType.UNKNOWN;
		this.currentLoggingStatus = LoggingStatus.NOTLOGGEDIN;
		this.currentUser = null;
		this.oldFile = null;
	}
  
	private boolean isFileNameValid(final String args) {
		return args != null && !args.isEmpty() && !args.startsWith("-");
	}
  
	private File getFileDesc(final String args) {
		final File	current;
		  
		if (Utils.checkEmptyOrNullString(args)) {
			current = new File(root, currDirectory);
		} 
		else if (args.startsWith("/")) {
			current = new File(root, args);
		} 
		else if (".".equals(args)) {
			current = new File(root, currDirectory);
		} 
		else if ("..".equals(args)) {
			if ("/".equals(currDirectory)) {
				current = root.getAbsoluteFile();
			}
			else {
				current = new File(root, currDirectory).getParentFile();
			}
		} else {
			current = new File(new File(root, currDirectory), args);
		}
		return current;
	}

	private String getFileName(final File	file) {
		final String	currentName = file.getAbsolutePath();
		final String	rootName = root.getAbsolutePath();
	
		if (rootName.length() >= currentName.length()) {
			return "/";
		}
		else {
			return currentName.substring(rootName.length()).replace('\\', '/');
		}
	}

	private Set<PosixFilePermission> getFilePermissions(final File file) throws IOException {
		final Path	path = file.toPath();
	  
		try {
			return Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
		} catch (UnsupportedOperationException exc) {
			final Set<PosixFilePermission>	result = new HashSet<>();
		  
			if (file.canRead()) {
				result.add(PosixFilePermission.OWNER_READ);
				result.add(PosixFilePermission.GROUP_READ);
				result.add(PosixFilePermission.OTHERS_READ);
			}
			if (file.canWrite()) {
				result.add(PosixFilePermission.OWNER_WRITE);
				result.add(PosixFilePermission.GROUP_WRITE);
				result.add(PosixFilePermission.OTHERS_WRITE);
			}
			if (file.canExecute()) {
				result.add(PosixFilePermission.OWNER_EXECUTE);
				result.add(PosixFilePermission.GROUP_EXECUTE);
				result.add(PosixFilePermission.OTHERS_EXECUTE);
			}
			return result;
		}
	}

	private String getFileGroup(final File file) throws IOException {
		final Path	path = file.toPath();
	  
		try {
			return Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS).group().getName();
		} catch (UnsupportedOperationException exc) {
			return Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName();
		}
	}  

	private SocketAddress openDataConnectionPassive(final int port) {
		conn.close();
		return conn.openPassive(port);
	}

	private void waitDataConnectionPassive(final int port) {
		conn.waitPassive(port);
	}
  
	private void openDataConnectionActive(final String ipAddress, final int port) {
		if (conn.mode != ConnectionMode.ACTIVE) {
			if (conn.mode != ConnectionMode.NONE) {
				conn.close();
			}
			conn.openActive(ipAddress, port);
		}
	}

	private void closeDataConnection() {
		conn.close();
		debug("Data connection was closed");
	}

	private Future<?> startTransmission(final DataCopier copier) {
        return service.submit((Runnable)()->{
			final long	start = System.currentTimeMillis();
			
	        try {
	        	copier.run();
		        final long	endT = System.currentTimeMillis();

		        debug("Completed file transmission of " + copier.file.getName());
				sendAnswer(MessageType.MSG_TRANSFER_COMPLETED_DETAILED, copier.processed, 0.001 * copier.processed / Math.max(1, endT - start), copier.file.getName());
			} catch (Throwable e) {
				sendAnswerSilent(MessageType.MSG_ABORT_DATA_CONNECTION);
			} finally {
	        	closeDataConnection();
			}
        });
	}
	
	private File[] getDirContent(final File current) {
		if (current.exists()) {
			if (current.isDirectory()) {
				final File[] items = current.listFiles();
		    	
				return items == null ? EMPTY_FILE_ARRAY : items; 
			}
			else {
				return new File[] {current};
			}
		}
		else {
			return null;
		}
	}

	private class DataConnection {
		private ConnectionMode	mode = ConnectionMode.NONE;
		private ServerSocket 		dataSocket;
		private Socket 			dataConnection;
		private OutputStream		os;
		private Writer			writer;
		
		void openActive(final String ipAddress, final int port) {
			if (mode == ConnectionMode.NONE) {
				try {
					dataConnection = new Socket(ipAddress, port);
					os = dataConnection.getOutputStream();
					writer = new OutputStreamWriter(os);
					mode = ConnectionMode.ACTIVE;
					debug("Data connection - Active Mode - established");
				} catch (IOException e) {
					debug("Could not connect to client data socket");
				}
			}
			else {
				throw new IllegalStateException("Attempt to open already opened connection");
			}
		}

		SocketAddress openPassive(final int port) {
			if (mode == ConnectionMode.NONE) {
				try {
					dataSocket = new ServerSocket(port);
					return new InetSocketAddress(controlSocket.getLocalAddress(), dataSocket.getLocalPort());
				} catch (IOException e) {
					debug("Could not create data connection (port "+port+")");
					return null;
				}
			}
			else {
				throw new IllegalStateException("Attempt to open already opened connection");
			}
		}	  
	  
		void waitPassive(final int port) {
			if (mode == ConnectionMode.NONE) {
				try {
					dataConnection = dataSocket.accept();
					os = dataConnection.getOutputStream();
					writer = new OutputStreamWriter(os);
					mode = ConnectionMode.PASSIVE;
					dataSocket.close();
					debug("Data connection - Passive Mode - established");
				} catch (IOException e) {
					debug("Could not create data connection (port "+port+")");
				}
			}
			else {
				throw new IllegalStateException("Attempt to open already opened connection");
			}
		}
	  
		boolean isConnectionValid() {
			return dataConnection != null && !dataConnection.isClosed();	  
		}
	
		InputStream getInputStream() throws IOException {
			if (mode == ConnectionMode.NONE) {
				throw new IllegalStateException("Attempt to get stream on closed socket"); 
			}
			else {
				return dataConnection.getInputStream();
			}
		}
	
		OutputStream getOutputStream() throws IOException {
			if (mode == ConnectionMode.NONE) {
				throw new IllegalStateException("Attempt to get stream on closed socket"); 
			}
			else {
				return os;
			}
		}
	
		Writer getWriter() {
			if (mode == ConnectionMode.NONE) {
				throw new IllegalStateException("Attempt to get stream on closed socket"); 
			}
			else {
				return writer;
			}
		}
	  
		void close() {
			if (mode!= ConnectionMode.NONE) {
				try {
					mode = ConnectionMode.NONE;
					writer.flush();
					writer.close();
					dataConnection.close();
					dataConnection = null;
					if (dataSocket != null && !dataSocket.isClosed()) {
						dataSocket.close();
						dataSocket = null;
					}
				} catch (IOException e) {
					debug("Could not close data connection");
					e.printStackTrace();
				}
			}
		}
	}
  
	private static class DataCopier extends Thread implements ProgressIndicator {
		private static final int	OP_RETR_BIN = 0;
		private static final int	OP_RETR_ASCII = 1;
		private static final int	OP_STOR_BIN = 2;
		private static final int	OP_STOR_ASCII = 3;
		private static final AtomicInteger	UNIQUE = new AtomicInteger(1);
	  
		private final int				operation;
		private final int				unique = UNIQUE.incrementAndGet();
		private final InputStream		is;
		private final Reader			rdr;
		private final OutputStream		os;
		private final Writer			wr;
		private final File				file;
		private final boolean			append;
		private volatile long			total = 0;
		private volatile long			processed = 0;
		private volatile boolean		terminate = false;
		private volatile boolean		processing = false;
		private volatile boolean		error = false;
	  
		private DataCopier(final InputStream from, final File to, final boolean append) {
			if (from == null) {
				throw new NullPointerException("From parameter can't be null");
			}
			else if (to == null) {
				throw new NullPointerException("To parameter can't be null");
			}
			else {
				this.operation = OP_STOR_BIN;
				this.is = from;
				this.rdr = null;
				this.os = null;
				this.wr = null;
				this.file = to;
				this.append = append;
				prepare();
			}
		}

		private DataCopier(final Reader from, final File to, final boolean append) {
			if (from == null) {
				throw new NullPointerException("From parameter can't be null");
			}
			else if (to == null) {
				throw new NullPointerException("To parameter can't be null");
			}
			else {
				this.operation = OP_STOR_ASCII;
				this.is = null;
				this.rdr = from;
				this.os = null;
				this.wr = null;
				this.file = to;
				this.append = append;
				prepare();
			}
		}
	  
		private DataCopier(final File from, final OutputStream to) {
			if (from == null) {
				throw new NullPointerException("From parameter can't be null");
			}
			else if (to == null) {
				throw new NullPointerException("To parameter can't be null");
			}
			else {
				this.operation = OP_RETR_BIN;
				this.is = null;
				this.rdr = null;	
				this.os = to;
				this.wr = null;
				this.file = from;
				this.append = false;
				prepare();
			}
		}

		private DataCopier(final File from, final Writer to) {
			if (from == null) {
				throw new NullPointerException("From parameter can't be null");
			}
			else if (to == null) {
				throw new NullPointerException("To parameter can't be null");
			}
			else {
				this.operation = OP_RETR_ASCII;
				this.is = null;
				this.rdr = null;
				this.os = null;
				this.wr = to;
				this.file = from;
				this.append = false;
				prepare();
			}
		}
	  
		@Override
		public void run() {
			switch (operation) {
			  	case OP_RETR_BIN	:
			  		start("", file.length());
			  		try(final InputStream	from = new FileInputStream(file)) {
			  			Utils.copyStream(from, os, this);
					} catch (IOException e) {
						error = true;
					}
			  		end();
			  		break;
			  	case OP_RETR_ASCII	:
			  		start("", file.length());
			  		try(final InputStream	from = new FileInputStream(file);
			  			final Reader		fromR = new InputStreamReader(from)) {
			  			Utils.copyStream(fromR, wr, this);
					} catch (IOException e) {
						error = true;
					}
			  		end();
			  		break;
			  	case OP_STOR_BIN	:
			  		start("");
			  		try(final OutputStream	to = new FileOutputStream(file, append)) {
			  			Utils.copyStream(is, to, this);
					} catch (IOException e) {
						error = true;
			  		}
			  		end();
			  		break;
			  	case OP_STOR_ASCII	:
			  		start("");
			  		try(final OutputStream	to = new FileOutputStream(file, append);
			  			final Writer		toW = new OutputStreamWriter(to)) {
			  			Utils.copyStream(rdr, toW, this);
					} catch (IOException e) {
						error = true;
			  		}
			  		end();
			  		break;
			  	default :
			  		throw new UnsupportedOperationException("Operation type ["+operation+"] is not supported yet");
			}
		}

		@Override
		public void start(final String caption, long total) {
			this.total = total;
			this.processing = true;
		}

		@Override
		public void start(final String caption) {
			this.processing = true;
		}

		@Override
		public void interrupt() {
			super.interrupt();
			terminate = true;
		}
	
		@Override
		public boolean processed(final long processed) {
			return !terminate && !Thread.interrupted();
		}

		@Override
		public void end() {
			this.processing = false;
		}
	  
		private void prepare() {
			setName("Data copier ["+unique+"] for "+file.getName());
			setDaemon(true);
		}
	}
}
