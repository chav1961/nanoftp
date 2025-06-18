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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import chav1961.nanoftp.utils.InternalUtils;
import chav1961.purelib.basic.Utils;
import chav1961.purelib.basic.interfaces.LoggerFacade;
import chav1961.purelib.basic.interfaces.LoggerFacade.Severity;
import chav1961.purelib.basic.interfaces.LoggerFacadeOwner;
import chav1961.purelib.basic.interfaces.ProgressIndicator;

class FTPSession implements Runnable, LoggerFacadeOwner {
	private static final File[]	EMPTY_FILE_ARRAY = new File[0];
	private static final String	EOL = "\r\n";

	static enum LoggingStatus {
		NOTLOGGEDIN,
		USERNAMEENTERED,
		LOGGEDIN,
		RENAMESTARTED,
		UNKNOWN,
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
	private final boolean 			supportRFC2228;
	private final boolean 			supportRFC2428;
	private final boolean 			supportRFC2640;
	private final boolean 			supportRFC3659;
	private final EnumSet<Commands>	blackList;
	private final boolean 			debugMode;
	private final SimpleValidator	validator;

	private String 				currDirectory = "/";
	private Writer 				controlOutWriter;
	private TransferType 		transferMode;
	private LoggingStatus 		currentLoggingStatus;
	private Locale				langLocale = Locale.getDefault();
	private String				currentUser;
	private File				oldFile;
	private DataCopier			copier = null;
	private Future<?>			future = null;
	private boolean				ignoreEPSV = false;
	private boolean				isUTF8On = false;
  
	FTPSession(final Socket client, final int dataPort, final ExecutorService service, final LoggerFacade logger, final File root, final SimpleValidator validator, final boolean supportRFC2228, final boolean supportRFC2428, final boolean supportRFC2640, final boolean supportRFC3659, final EnumSet<Commands> blackList, final boolean debugMode) {
	    this.controlSocket = client;
	    this.dataPort = dataPort;
	    this.service = service;
	    this.logger = logger;
	    this.validator = validator;
	    this.supportRFC2228 = supportRFC2228;
	    this.supportRFC2428 = supportRFC2428;
	    this.supportRFC2640 = supportRFC2640;
	    this.supportRFC3659 = supportRFC3659;
	    this.blackList = blackList;
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
		debug("FTP session started, remote address is ["+controlSocket.getRemoteSocketAddress()+"], current working directory is <" + this.currDirectory + ">");

		try(final Socket	s = controlSocket;
			final Reader	rdr = supportRFC2640 ? new InputStreamReader(s.getInputStream(), "UTF-8") : new InputStreamReader(s.getInputStream());
			final BufferedReader	controlIn = new BufferedReader(rdr);
			final Writer	controlOutWriter = new OutputStreamWriter(s.getOutputStream())) {
			String			line;

			this.controlOutWriter = controlOutWriter;
			sendAnswer(MessageType.MSG_WELCOME);
			while ((line = controlIn.readLine()) != null) {
				if (!executeCommand(line)) {
					break;
				}
			}
		} catch (Exception e) {
			if (future != null && !future.isDone()) {
				future.cancel(true);
			}
			if (debugMode) {
				e.printStackTrace();
			}
		} finally {
			debug("FTP session on ["+controlSocket.getRemoteSocketAddress()+"] ended");
		}
	}

	private boolean executeCommand(final String c) throws IOException {
		final int 		blank = c.indexOf(' ');
		final String 	command = blank == -1 ? c : c.substring(0, blank);
		final String 	args = blank == -1 ? "" : c.substring(blank + 1).trim();
		
		try {
			final Commands	cmd = Commands.valueOf(command.trim().toUpperCase());

			debug("Command: " + cmd + ", args: <" + (cmd == Commands.PASS ? "***" : args) + ">");
			if(cmd.isFeature() && !isFeatureSupported(cmd)) {
				sendAnswer(MessageType.MSG_UNSUPPORTED_COMMAND);
				return true;
			}
			else if(blackList.contains(cmd)) {
				sendAnswer(MessageType.MSG_IGNORED_COMMAND);
				return true;
			}
			else if (cmd.getContext() != null && cmd.getContext() != currentLoggingStatus) {
				sendAnswer(MessageType.MSG_USER_NOT_LOGGED);
				return true;
			}
			else {
				try {
					switch (cmd) {
						// Chapter 4.1.1 RFC-959.
						case USER:
					  		handleUser(args);
							break;
						case PASS:
					  		handlePass(args);
							break;
						case ACCT:
					  		handleAcct();
							break;
						case CWD:
						case XCWD:
					  		handleCwd(args);
							break;
						case CDUP:
					  		handleCwd("..");
							break;
						case SMNT:
					  		handleSmnt(args);
							break;
						case REIN:
					  		handleRein();
							break;
						case QUIT:
					  		handleQuit();
							break;
						// Chapter 4.1.2 RFC-959.
						case PORT:
							if (ignoreEPSV) {
								sendAnswer(MessageType.MSG_IGNORED_COMMAND_BY_EPSV);
							}
							else {
								handlePort(args);
							}
							break;
						case PASV:
							if (ignoreEPSV) {
								sendAnswer(MessageType.MSG_IGNORED_COMMAND_BY_EPSV);
							}
							else {
								handlePasv();
							}
							break;
						case TYPE:
					  		handleType(args.toUpperCase());
							break;
						case STRU:
					  		handleStru(args.toUpperCase());
							break;
						case MODE:
					  		handleMode(args.toUpperCase());
							break;
						// Chapter 4.1.3 RFC-959.
						case RETR:
					  		handleRetr(args);
							break;
						case STOR:
					  		handleStor(args, false, false);
							break;
						case STOU:
					  		handleStor(args, false, true);
							break;
						case APPE:
					  		handleStor(args, true, false);
							break;
						case ALLO:
					  		handleAllo(args);
							break;
						case REST:
					  		handleRest(args);
							break;
						case RNFR:
					  		handleRnfr(args);
							break;
						case RNTO:
					  		handleRnto(args);
							break;
						case ABOR:
					  		handleAbor();
							break;
						case DELE:
					  		handleDele(args);
							break;
						case RMD:
						case XRMD:
					  		handleRmd(args);
							break;
						case MKD:
						case XMKD:
					  		handleMkd(args);
							break;
						case PWD:
						case XPWD:
					  		handlePwd();
							break;
						case LIST:
					  		handleList(args);
							break;
						case NLST:
					  		handleNlst(args);
							break;
						case SITE:	// TODO:
					  		handleSite();
							break;
						case SYST:
					  		handleSyst();
							break;
						case STAT:
					  		handleStat(args);
							break;
						case HELP:
					  		handleHelp(args);
							break;
						case NOOP:
					  		handleNoop();
							break;
						case FEAT:
							if (hasAnyFeaturesOn()) {
						  		handleFeat();
							}
							else {
								sendAnswer(MessageType.MSG_UNKNOWN_COMMAND);
							}
							break;
						case OPTS:
							if (hasAnyFeaturesOn()) {
								handleOpts(args);
							}
							else {
								sendAnswer(MessageType.MSG_UNKNOWN_COMMAND);
							}
							break;
						// RFC-2228.
						case AUTH:
							sendAnswer(MessageType.MSG_UNSUPPORTED_AUTH_EXTENSION);
							break;
						case ADAT:	// TODO:
						case PROT:	// TODO:
						case PBSZ:	// TODO:
						case CCC:	// TODO:
						case XCCC:	// TODO:
						case MIC:	// TODO:
						case XMIC:	// TODO:
						case CONF:	// TODO:
						case ENC:	// TODO:
						case XENC:	// TODO:							
					  		throw new UnsupportedOperationException("Command ["+c+"] is not supported yet");
						// RFC-2428.
						case EPRT:
							if (ignoreEPSV) {
								sendAnswer(MessageType.MSG_IGNORED_COMMAND_BY_EPSV);
							}
							else {
						  		handleEPort(args);
							}
							break;
						case EPSV:
							if ("ALL".equals(args)) {
								ignoreEPSV = true;
								sendAnswer(MessageType.MSG_COMMAND_OK);
							}
							else {
						  		handleEpsv();
							}
							break;
						// RFC-2640.
						case LANG:
							handleLang(args);
							break;
						// RFC-3659.
						case SIZE:
					  		handleSize(args);
							break;
						case MDTM:
					  		handleMdtm(args);
							break;
						case TVFS:
					  		handleTvfs();
						case MLST:
							handleMlst(args.isEmpty() ? currDirectory : args);
							break;
						case MLSD:
							handleMlsd(args.isEmpty() ? currDirectory : args);
							break;
						default:
					  		throw new UnsupportedOperationException("Command ["+c+"] is not supported yet");
					}
					return !cmd.isExitRequired();
				} catch (CommandParserException exc) {
					sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, exc.getLocalizedMessage());
					return true;
				} catch (IllegalArgumentException exc) {
					sendAnswer(MessageType.MSG_ILLEGAL_ARGUMENT, exc.getLocalizedMessage());
					return true;
				}
			}
		} catch (IllegalArgumentException exc) {
			debug("Wrong command: " + c);
			sendAnswer(MessageType.MSG_UNKNOWN_COMMAND);
			return true;
		}
	}

	private boolean isFeatureSupported(final Commands cmd) {
		return cmd.isRFC2228() && supportRFC2228 || cmd.isRFC2428() && supportRFC2428 || cmd.isRFC2640() && supportRFC2640 || cmd.isRFC3659() && supportRFC3659;
	}

	private boolean hasAnyFeaturesOn() {
		return supportRFC2228 || supportRFC2428 || supportRFC2640 || supportRFC3659;
	}
	
	private void handleRein() throws IOException {
		clearSettings();
		sendAnswer(MessageType.MSG_CONNECTION_RESET);
	}  

	private void handleLang(final String langTag) throws IOException, CommandParserException {
		if (Utils.checkEmptyOrNullString(langTag)) {
			langLocale = Locale.getDefault();
		}
		else {
			try {
				langLocale = Locale.forLanguageTag(langTag);
			} catch (IllegalArgumentException exc) {
				throw new CommandParserException(MessageType.MSG_ILLEGAL_ARGUMENT, langTag);
			}
		}
		sendAnswer(MessageType.MSG_COMMAND_OK);
	}
	
	private void handleUser(final String userName) throws IOException {
		if (Utils.checkEmptyOrNullString(userName)) {
			throw new IllegalArgumentException("User name string can't be null or empty");
		}
		else {
			switch (currentLoggingStatus) {
				case LOGGEDIN		:
					handleRein();
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
			throw new IllegalArgumentException("Password string can't be null or empty");
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
						sendAnswer(MessageType.MSG_WRONG_CREDENTIALS);
						sendAnswer(MessageType.MSG_WELCOME);
						currentLoggingStatus = LoggingStatus.NOTLOGGEDIN;
					}
					break;
				default:
					throw new UnsupportedOperationException("Logging status ["+currentLoggingStatus+"] is not supported yet");
			}
		}
	}

	private void handleCwd(final String args) throws IOException {
		final File	current = getFileDesc(args.replace(File.separatorChar, '/'));

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
		sendAnswer(MessageType.MSG_CURRENT_DIR, currDirectory);
	}

	private void handlePort(final String args) throws IOException {
		// args: ip1,ip2,ip3,ip4,port/256,port%256
		if (args.matches("\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3}")) {
			final String[] 	content = args.split(",");
			final String 	hostName = content[0].trim() + '.' + content[1].trim() + '.' + content[2].trim() + '.' + content[3].trim();
			final int 		port = Integer.parseInt(content[4].trim()) * 256 + Integer.parseInt(content[5].trim());
		
			if (openDataConnectionActive(hostName, port)) {
				sendAnswer(MessageType.MSG_COMMAND_OK);
			}
			else {
				sendAnswer(MessageType.MSG_PORT_CONNECTION_FAILURE, hostName, port);
			}
		}
		else {
			throw new IllegalArgumentException(args);
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
			throw new IllegalArgumentException(args);
		}
	}

	private void handlePasv() throws IOException {
		final SocketAddress	addr = openDataConnectionPassive(dataPort);
		
		if (addr != null) {
		    final String	host = ((InetSocketAddress)addr).getAddress().getHostAddress();
		    final int		port = ((InetSocketAddress)addr).getPort();
		    final String[]	ip = host.split("\\."); 
		    final int 		p1 = port / 256;
		    final int 		p2 = port % 256;
		    
		    sendAnswer(MessageType.MSG_ENTERING_PASSIVE_MODE, ip[0], ip[1], ip[2], ip[3], p1, p2);
		    waitDataConnectionPassive(port);
		}
		else {
		    sendAnswer(MessageType.MSG_PASV_CONNECTION_FAILURE);
		}
	}

	private void handleEpsv() throws IOException {
		final SocketAddress	addr = openDataConnectionPassive(dataPort);
		final int		port = ((InetSocketAddress)addr).getPort();
	  
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
		sendBlock(MessageType.MSG_EXTENSIONS_START, MessageType.MSG_EXTENSIONS_END, Commands.values(), (v)->{
			if (v.isFeature() && !blackList.contains(v)) {
				return v.getFeatureString();
			}
			else {
				return null;
			}
		});
	}

	private void handleOpts(final String args) throws IOException, CommandParserException {
		// TODO Auto-generated method stub
		if (Utils.checkEmptyOrNullString(args)) {
			throw new IllegalArgumentException("'OPTS' arguments can't be null or empty");
		}
		else {
			final int 		blank = args.indexOf(' ');
			final String 	command = blank == -1 ? args : args.substring(0, blank);
			final String 	parm = blank == -1 ? "" : args.substring(blank + 1).trim();
			final Commands	cmd = Commands.valueOf(command.trim().toUpperCase());

			switch (cmd) {
				case UTF8:
					if ("ON".equalsIgnoreCase(parm) || "OFF".equalsIgnoreCase(parm)) {
						isUTF8On = "ON".equalsIgnoreCase(parm); 
						sendAnswer(MessageType.MSG_COMMAND_OK);
					}
					else {
						throw new CommandParserException(MessageType.MSG_ILLEGAL_ARGUMENT, parm);
					}
					break;
				default :
					sendAnswer(MessageType.MSG_COMMAND_IGNORED);
					break;
			}
		}
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
			throw new IllegalArgumentException(args);
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
			throw new IllegalArgumentException(dir);
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
			throw new IllegalArgumentException(file);
		}
	}

	private void handleSite() throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_IGNORED);
	}
  
	private void handleType(final String mode) throws IOException, CommandParserException {
		transferMode = RepresentationTypeDescriptor.valueOf(mode).type;
		sendAnswer(MessageType.MSG_COMMAND_OK);
	}

	private void handleMode(final String mode) throws IOException {
		if (Utils.checkEmptyOrNullString(mode) || !mode.matches("(S|B|C)")) {
			throw new IllegalArgumentException(mode);
		}
		else {
			switch(mode) {
				case "B" : case "C" :	// TODO:
					sendAnswer(MessageType.MSG_UNSUPPORTED_ARGUMENT, mode);
					break;
				case "S" :
					sendAnswer(MessageType.MSG_COMMAND_OK);
					break;
				default :
					throw new UnsupportedOperationException("Mode ["+mode+"] is not supported yet");
			}
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
			throw new IllegalArgumentException(file);
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
			throw new IllegalArgumentException(size);
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
			throw new IllegalArgumentException(file);
		}
	}

	private void handleRest(final String displ) throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_IGNORED);
	}  

	private void handleRnfr(final String file) throws IOException {
		final File	f = getFileDesc(file);
	  
		if (f.exists() && f.isFile()) {
			oldFile = f;
			currentLoggingStatus = LoggingStatus.RENAMESTARTED;
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
		else {
			if (oldFile.renameTo(f)) {
				sendAnswer(MessageType.MSG_COMMAND_OK);
			}
			else {
				sendAnswer(MessageType.MSG_FAILURE_FILE_UNAVAILABLE, getFileName(f));
			}
			currentLoggingStatus = LoggingStatus.LOGGEDIN;
			oldFile = null;
		}
	}  

	private void handleNoop() throws IOException {
		sendAnswer(MessageType.MSG_COMMAND_OK);
	}  

	private void handleHelp(final String name) throws IOException {
		if (Utils.checkEmptyOrNullString(name)) {
			sendBlock(MessageType.MSG_COMMANDS_START, MessageType.MSG_COMMANDS_END, Commands.values(), (v)->{
				if (!v.isFeature()) {
					return v.name() + ' ' + v.getArgs();
				}
				else {
					return v.name() + ' ' + v.getArgs() + " (feature)";
				}
			});
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
			throw new IllegalArgumentException(parm);
		}
		else {
			switch (parm) {
				case "F" : 
					sendAnswer(MessageType.MSG_COMMAND_OK);
					break;
				case "P" : case "R" :	// TODO:
					sendAnswer(MessageType.MSG_UNSUPPORTED_ARGUMENT, parm);
					break;
				default :
					throw new UnsupportedOperationException("Structure ["+parm+"] is not supported yet");
			}
		}
	}  

	private void handleSmnt(final String fileName) throws IOException {
		// TODO:
		final File		current = new File(root, fileName).getAbsoluteFile();
		
		if (!current.exists()) {
			sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS, getFileName(current));
		}
		else {
			sendAnswer(MessageType.MSG_COMMAND_IGNORED);
		}
	}  

	private void handleMlst(final String fileName) throws IOException {
		final File		current = new File(root, fileName).getAbsoluteFile();
		
		if (!current.exists()) {
			sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS, getFileName(current));
		}
		else {
			sendBlock(MessageType.MSG_FILE_DESC_BEGIN, MessageType.MSG_FILE_DESC_END, new String[] {new MLSDResponse(current).getDescriptor()}, (v)->v);
//			sendAnswer(MessageType.MSG_FILE_DESC_BEGIN);
//			sendCommandLine(" "+new MLSDResponse(current).getDescriptor() + EOL);
//			sendAnswer(MessageType.MSG_FILE_DESC_END);
		}
	}
	
	private void handleMlsd(final String dirName) throws IOException {
		if (!conn.isConnectionValid()) {
			sendAnswer(MessageType.MSG_NO_DATA_CONNECTION);
		} 
		else {
			final File		current = new File(root, dirName).getAbsoluteFile();
			
			if (!current.exists()) {
				sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS, getFileName(current));
			}
			else {
				final File[] 	dirContent = getDirContent(current);

				sendAnswer(MessageType.MSG_OPEN_BINARY_CONN_FOR_LIST);
				sendDataLine("type=cdir; .");
				if (!getFileName(current).equals("/")) {
					sendDataLine("type=pdir; ..");
				}
				if (dirContent != null) {
					for (File content : dirContent) {
						sendDataLine(new MLSDResponse(content).getDescriptor());
					}
				}
				sendAnswer(MessageType.MSG_TRANSFER_COMPLETED);
				closeDataConnection();
			}
		}
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

	private void handleMdtm(final String file) throws IOException {
		final File	f = getFileDesc(file);
	  
		if (f.exists() && f.isFile()) {
			sendAnswer(MessageType.MSG_FILE_MODIFICATION_TIME, InternalUtils.milliseconds2Time(f.lastModified()));
		}
		else {
			sendAnswer(MessageType.MSG_FAILURE_FILE_NOT_EXISTS);
		}
	}  

	private void handleTvfs() throws IOException {
		sendAnswer(MessageType.MSG_UNKNOWN_COMMAND);
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
			wr.write(EOL);
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

	private String getFileName(final File file) {
		final String	currentName = file.getAbsolutePath();
		final String	rootName = root.getAbsolutePath();
	
		if (rootName.length() >= currentName.length()) {
			return "/";
		}
		else {
			return currentName.substring(rootName.length()).replace(File.separatorChar, '/');
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
  
	private boolean openDataConnectionActive(final String ipAddress, final int port) {
		if (conn.mode != ConnectionMode.ACTIVE) {
			if (conn.mode != ConnectionMode.NONE) {
				conn.close();
			}
			return conn.openActive(ipAddress, port);
		}
		else {
			return true;
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

	private <T> void sendBlock(final MessageType beginBlock, final MessageType endBlock, final Iterable<T> content, final Function<T,String> formatter) throws IOException {
		sendAnswer(beginBlock);
		for(T item : content) {
			final String	val = formatter.apply(item);
			
			if (val != null) {
				sendCommandLine(' ' + val + EOL);
			}
		}
		sendAnswer(endBlock);
	}

	private <T> void sendBlock(final MessageType beginBlock, final MessageType endBlock, final T[] content, final Function<T,String> formatter) throws IOException {
		sendAnswer(beginBlock);
		for(T item : content) {
			final String	val = formatter.apply(item);
			
			if (val != null) {
				sendCommandLine(' ' + val + EOL);
			}
		}
		sendAnswer(endBlock);
	}
	
	static class RepresentationTypeDescriptor {
		private final TransferType	type;
		private final char	modifier;

		public RepresentationTypeDescriptor(final TransferType type, final char modifier) {
			this.type = type;
			this.modifier = modifier;
		}

		@Override
		public String toString() {
			return "RepresentationDescriptor [type=" + type + ", modifier=" + modifier + "]";
		}
		
		private static RepresentationTypeDescriptor valueOf(final String type) throws CommandParserException {
			if (Utils.checkEmptyOrNullString(type)) {
				throw new IllegalArgumentException("Representation type string can't be null or empty");
			}
			else if (type.matches("((A|E|I)(\\s+(N|T|C)){0,1}|L\\s+\\d+)")) {
				final String[]	modes = type.split("\\s+");
			  
				switch (modes[0].charAt(0)) {
					case 'A' :   
						if (modes.length > 1) {
							switch (modes[1]) {
								case "N" :
					  				return new RepresentationTypeDescriptor(TransferType.ASCII, '0');
								case "T" : case "C" : // TODO:
					  				throw new CommandParserException(MessageType.MSG_UNSUPPORTED_ARGUMENT, type);
								default :
									throw new UnsupportedOperationException("'A' mode argument ["+modes[1]+"] is not suoported yet");
							}
						}
						else {
			  				return new RepresentationTypeDescriptor(TransferType.ASCII, '0');
						}
					case 'I' :   
		  				return new RepresentationTypeDescriptor(TransferType.BINARY, '0');
					case 'L' : 
		  				if (Integer.parseInt(modes[1]) != 8) {
					  		throw new CommandParserException(MessageType.MSG_UNSUPPORTED_ARGUMENT, type);
		  				}
		  				else {
			  				return new RepresentationTypeDescriptor(TransferType.BINARY, '0');
		  				}
				  	case 'E' : 
				  		throw new CommandParserException(MessageType.MSG_UNSUPPORTED_ARGUMENT, type);
				  	default :
				  		throw new CommandParserException(MessageType.MSG_ILLEGAL_ARGUMENT, type);
				}
			}
			else {
		  		throw new CommandParserException(MessageType.MSG_ILLEGAL_ARGUMENT, type);
			}
		}
	}
	
	private class DataConnection {
		private ConnectionMode	mode = ConnectionMode.NONE;
		private ServerSocket 	dataSocket;
		private Socket 			dataConnection;
		private OutputStream	os;
		private Writer			writer;
		
		boolean openActive(final String ipAddress, final int port) {
			if (mode == ConnectionMode.NONE) {
				try {
					dataConnection = new Socket(ipAddress, port);
					os = dataConnection.getOutputStream();
					writer = new OutputStreamWriter(os);
					mode = ConnectionMode.ACTIVE;
					debug("Data connection - Active Mode - established");
					return true;
				} catch (IOException e) {
					debug("Could not connect to client data socket");
					return false;
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
			  			
			  			processed = Utils.copyStream(from, os, this);
					} catch (IOException e) {
						error = true;
					}
			  		end();
			  		break;
			  	case OP_RETR_ASCII	:
			  		start("", file.length());
			  		try(final InputStream	from = new FileInputStream(file);
			  			final Reader		fromR = new InputStreamReader(from)) {
			  			
			  			processed = Utils.copyStream(fromR, wr, this);
					} catch (IOException e) {
						error = true;
					}
			  		end();
			  		break;
			  	case OP_STOR_BIN	:
			  		start("");
			  		try(final OutputStream	to = new FileOutputStream(file, append)) {
			  			
			  			processed = Utils.copyStream(is, to, this);
					} catch (IOException e) {
						error = true;
			  		}
			  		end();
			  		break;
			  	case OP_STOR_ASCII	:
			  		start("");
			  		try(final OutputStream	to = new FileOutputStream(file, append);
			  			final Writer		toW = new OutputStreamWriter(to)) {
			  			
			  			processed = Utils.copyStream(rdr, toW, this);
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
	
	
	private static class MLSDResponse {
		private static final String	FACT_SIZE = "size";
		private static final String	FACT_MODIFY = "modify";
		private static final String	FACT_TYPE = "type";
		private static final String	FACT_PERM = "perm";
		private static final String	FACT_LANG = "lang";
		
		private final File		file;
		
		private MLSDResponse(final File file) {
			this.file = file;
		}

		public String getDescriptor() {
			final StringBuilder	sb = new StringBuilder();
			
			sb.append(FACT_TYPE).append('=').append(file.isDirectory() ? "dir" : "file").append(';');
			sb.append(FACT_MODIFY).append('=').append(InternalUtils.milliseconds2Time(file.lastModified())).append(';');
			sb.append(FACT_PERM).append('=').append(calcPermissions(file)).append(';');
			sb.append(FACT_LANG).append('=').append(Locale.getDefault().getLanguage()).append(';');
			sb.append(FACT_SIZE).append('=').append(file.length()).append(';');
			sb.append(' ').append(file.getName().replace(File.separatorChar, '/'));
			return sb.toString();
		}

		private String calcPermissions(final File file) {
			final StringBuilder	sb = new StringBuilder();
			
			if (file.isDirectory()) {
				sb.append('e');
				if (file.canWrite()) {
					sb.append('c').append('f').append('l').append('m').append('p');
					if (file.listFiles() == null) {
						sb.append('d');
					}
				}
			}
			else {
				sb.append('r');
				if (file.canWrite()) {
					sb.append('a').append('d').append('w');
				}
			}
			return sb.toString();
		}
		
	}
}
