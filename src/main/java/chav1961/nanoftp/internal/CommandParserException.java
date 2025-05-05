package chav1961.nanoftp.internal;

public class CommandParserException extends Exception {
	private static final long 		serialVersionUID = 6880661968494390433L;
	private static final Object[]	DUMMY = new Object[0];
	
	private final MessageType	type;
	private final Object[]		parameters;

	public CommandParserException(final MessageType type) {
		super(type.getMessage());
		this.type = type;
		this.parameters = DUMMY;
	}
	
	public CommandParserException(final MessageType type, final Object... parameters) {
		super(type.getMessage());
		this.type = type;
		this.parameters = parameters;
	}

	public CommandParserException(final MessageType type, final Throwable cause) {
		super(type.getMessage(), cause);
		this.type = type;
		this.parameters = DUMMY;
	}

	public CommandParserException(final MessageType type, final Throwable cause, final Object... parameters) {
		super(type.getMessage(), cause);
		this.type = type;
		this.parameters = parameters;
	}
	
	public MessageType getMessageType() {
		return type;
	}
	
	public Object[] getParameters() {
		return parameters;
	}
}
