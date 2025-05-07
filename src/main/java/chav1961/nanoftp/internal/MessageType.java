package chav1961.nanoftp.internal;

enum MessageType {
	MSG_OPEN_CONN_FOR_LIST(125, " Opening ASCII mode data connection for file list.\r\n"),
	MSG_OPEN_BIN_CONN_FOR_FILE(150, " Opening binary mode data connection for file %1$s\r\n"),
	MSG_OPEN_ASCII_CONN_FOR_FILE(150, " Opening ASCII mode data connection for file %1$s\r\n"),
	MSG_OPEN_BINARY_CONN_FOR_LIST(150, " Opening binary mode data connection for file list.\r\n"),
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
	MSG_FILE_DESC_BEGIN(250, "- File descriptor \r\n"),	  
	MSG_FILE_DESC_END(250, " File descriptor end\r\n"),	  
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
	MSG_UNSUPPORTED_COMMAND(500, " Unsupported command (possibly, -rfcZZZZ key in the server command line is required)\r\n"),
	MSG_IGNORED_COMMAND(500, " Command ignored (-ignore key was typed)\r\n"),
	MSG_ILLEGAL_ARGUMENT(501, " Illegal argument [%1$s]\r\n"),
	MSG_MISSING_FILE_NAME(501, " File name missing\r\n"),
	MSG_MISSING_RNFR_BEFORE_RNTO(503, " RNTO command without RNFR preceding\r\n"),
	MSG_UNSUPPORTED_ARGUMENT(504, " Argument [%1$s] is not supported by the server\r\n"),
	MSG_TRANSFER_MODE_NOT_SET(504, " Transfer mode is not set yet\r\n"),
	MSG_USER_ALREADY_LOGGED(530," User already logged in\r\n"),
	MSG_USER_NOT_LOGGED(530," Command in wrong context (possibly not logged in)\r\n"),
	MSG_USER_NOT_ENTERED(530," User name is not entered yet\r\n"),
	MSG_WRONG_CREDENTIALS(530," Wrong credentials for user typed\r\n"),
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