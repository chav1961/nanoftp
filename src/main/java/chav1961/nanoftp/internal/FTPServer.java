package chav1961.nanoftp.internal;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import chav1961.purelib.basic.Utils;
import chav1961.purelib.basic.interfaces.LoggerFacade;
import chav1961.purelib.basic.interfaces.LoggerFacade.Severity;
import chav1961.purelib.basic.interfaces.LoggerFacadeOwner;
import chav1961.purelib.concurrent.interfaces.ExecutionControl;

public class FTPServer implements Runnable, ExecutionControl, LoggerFacadeOwner, AutoCloseable {
	private static final AtomicInteger	unique = new AtomicInteger(1);

	private final ExecutorService	exec = Executors.newCachedThreadPool((r)->{
											final Thread	t = new Thread(r); 
											
											t.setDaemon(true);
											t.setName("Async copier "+unique.incrementAndGet());
											return t;
									});
	private final LoggerFacade		logger = LoggerFacade.Factory.newInstance(URI.create(LoggerFacade.LOGGER_SCHEME+":err:/"));
	private final ServerSocket		ss;
	private final int				dataPort;
	private final File				root;
	private final SimpleValidator	validator;
	private final boolean			needDebug;
	private volatile boolean		isStarted = false;
	private volatile boolean		isSuspended = false;
	
	public FTPServer(final int serverPort, final int dataPort, final File root, final String userPass, final boolean needDebug) throws IOException {
		if (serverPort < 0 || serverPort > Character.MAX_VALUE) {
			throw new IllegalArgumentException("Server port ["+serverPort+"] out of range 0.."+(int)Character.MAX_VALUE);
		}
		else if (dataPort < 0 || dataPort > Character.MAX_VALUE) {
			throw new IllegalArgumentException("Data port ["+dataPort+"] out of range 0.."+(int)Character.MAX_VALUE);
		}
		else if (root == null || !root.exists() || !root.isDirectory() || !root.canRead()) {
			throw new IllegalArgumentException("Root file ["+root+"] is null, not exists, not a directory or not accessible for you");
		}
		else {
			this.ss = new ServerSocket(serverPort);
			this.dataPort = dataPort;
			this.root = root;
			this.validator = Utils.checkEmptyOrNullString(userPass) 
								? new SimpleValidator(root)
								: new SimpleValidator(userPass.split("/")[0], userPass.split("/")[1]);
			this.needDebug = needDebug;
		}
	}

	@Override
	public LoggerFacade getLogger() {
		return logger;
	}

	@Override
	public void run() {
		for (;;) {
			try {
				final Socket		sock = ss.accept();
				
				if (isStarted() && !isSuspended()) {
					final FTPSession 	w = new FTPSession(sock, dataPort, exec, logger, root, validator, needDebug);
					final Thread		t = new Thread(w);
		
					t.setDaemon(true);
					t.setName("FTP session ["+unique.incrementAndGet()+"] for "+sock.getRemoteSocketAddress());
					t.start();
				}
				else {
					try {
						sock.close();
					} catch (IOException exc) {
						continue;
					}
				}
			} catch (IOException exc) {
				break;
			}
		}
	}
	
	@Override
	public synchronized void start() throws IOException {
		if (isStarted()) {
			throw new IllegalStateException("Server is started already");
		}
		else {
			isStarted = true;
			if (needDebug) {
				getLogger().message(Severity.debug, "Server started");
			}
		}
	}

	@Override
	public synchronized void suspend() throws IOException {
		if (!isStarted()) {
			throw new IllegalStateException("Server is not started yet");
		}
		else if (isSuspended()) {
			throw new IllegalStateException("Server is suspended already");
		}
		else {
			isSuspended = true;
			if (needDebug) {
				getLogger().message(Severity.debug, "Server suspended");
			}
		}
	}

	@Override
	public synchronized void resume() throws IOException {
		if (!isStarted()) {
			throw new IllegalStateException("Server is not started yet");
		}
		else if (!isSuspended()) {
			throw new IllegalStateException("Server is not suspended yet");
		}
		else {
			isSuspended = false;
			if (needDebug) {
				getLogger().message(Severity.debug, "Server resumed");
			}
		}
	}

	@Override
	public synchronized void stop() throws IOException {
		if (!isStarted()) {
			throw new IllegalStateException("Server is not started yet");
		}
		else {
			isSuspended = false;
			isStarted = false;
			if (needDebug) {
				getLogger().message(Severity.debug, "Server stopped");
			}
		}
	}

	@Override
	public boolean isStarted() {
		return isStarted;
	}

	@Override
	public boolean isSuspended() {
		return isSuspended;
	}

	@Override
	public void close() throws IOException {
		ss.close();
		exec.shutdownNow();
		if (needDebug) {
			getLogger().message(Severity.debug, "Server closed");
		}
	}

	public synchronized void shutdown() throws IOException {
		ss.close();
	}
}
