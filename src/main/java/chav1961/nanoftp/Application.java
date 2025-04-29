package chav1961.nanoftp;


import java.io.File;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import chav1961.nanoftp.jmx.JmxManager;
import chav1961.purelib.basic.ArgParser;
import chav1961.purelib.basic.Utils;
import chav1961.purelib.basic.exceptions.CommandLineParametersException;
import chav1961.purelib.basic.interfaces.LoggerFacade;
import chav1961.purelib.basic.interfaces.LoggerFacade.Severity;

public class Application {
	public static final String	ARG_FTP_PORT = "port";
	public static final String	ARG_FTP_DATA_PORT = "dataPort";
	public static final String	ARG_FTP_ROOT = "root";
	public static final String	ARG_ANON_USER = "user";
	public static final String	ARG_JMX_ENABLE = "jmx";
	public static final String	ARG_DEBUG_TRACE = "d";
	public static final String	JMX_NAME = "chav1961.nanoftp:type=basic,name=server";

	public static final AtomicInteger	unique = new AtomicInteger(1);
	
	public static void main(String[] args) {
		final ArgParser	parser = new ApplicationArgParser();

		try(final LoggerFacade	logger = LoggerFacade.Factory.newInstance(URI.create(LoggerFacade.LOGGER_SCHEME+":err:/"))) {
			final ArgParser		parsed = parser.parse(args);
			final File			root = parsed.getValue(ARG_FTP_ROOT, File.class);
			final int			ftpPort = parsed.getValue(ARG_FTP_PORT, int.class);
			final int			ftpDataPort = parsed.getValue(ARG_FTP_DATA_PORT, int.class);
			final String		userPass = parsed.getValue(ARG_ANON_USER, String.class);
			final boolean		needDebug = parsed.getValue(ARG_DEBUG_TRACE, boolean.class);
			
			final SimpleValidator	validator = Utils.checkEmptyOrNullString(userPass) 
											? new SimpleValidator(root)
											: new SimpleValidator(userPass.split("/")[0], userPass.split("/")[1]);
			final ExecutorService	exec = Executors.newCachedThreadPool((r)->{
											final Thread	t = new Thread(r); 
											
											t.setDaemon(true);
											t.setName("Async copier "+unique.incrementAndGet());
											return t;
									});
			final ObjectName 		jmxName = new ObjectName(JMX_NAME);
			final MBeanServer 		server = ManagementFactory.getPlatformMBeanServer();
			final JmxManager		mgr = new JmxManager(null);

			if (parsed.getValue(ARG_JMX_ENABLE, boolean.class)) {
				server.registerMBean(mgr, jmxName);
//				if (wrapper.isTraceTurnedOn()) {
//					wrapper.getLogger().message(Severity.debug, "JMX server started, JMX name is ["+JMX_NAME+"]");
//				}
			}
			
			
			try(final ServerSocket		ss = new ServerSocket(ftpPort)) {
				Runtime.getRuntime().addShutdownHook(new Thread(()->{
					try {
						ss.close();
					} catch (IOException e) {
					}
				}));
				if (needDebug) {
					logger.message(Severity.info, "Nano FTP server listen on %1$d port", ftpPort);
				}
				for (;;) {
					try {
						final Socket		sock = ss.accept();
						
						final FTPSession 	w = new FTPSession(sock, ftpDataPort, exec, logger, root, validator, needDebug);
						final Thread		t = new Thread(w);
		
						t.setDaemon(true);
						t.setName("FTP session "+unique.incrementAndGet());
						t.start();
					} catch (IOException exc) {
						break;
					}
				}
				if (needDebug) {
					logger.message(Severity.info, "Nano FTP server stopped");
				}
				if (parsed.getValue(ARG_JMX_ENABLE, boolean.class)) {
					server.unregisterMBean(jmxName);
//					if (wrapper.isTraceTurnedOn()) {
//						wrapper.getLogger().message(Severity.debug, "JMX server started, JMX name is ["+JMX_NAME+"]");
//					}
				}
			} finally {
				exec.shutdownNow();
			}
		} catch (CommandLineParametersException e) {
			System.err.println(e.getLocalizedMessage());
			System.err.println(parser.getUsage("bt.nanoftp"));
			System.exit(128);
		} catch (IOException | MalformedObjectNameException | InstanceAlreadyExistsException | InstanceNotFoundException |MBeanRegistrationException | NotCompliantMBeanException e) {
			e.printStackTrace();
			System.exit(129);
		}
	}

	private static class ApplicationArgParser extends ArgParser {
		private static final ArgParser.AbstractArg[]	KEYS = {
			new IntegerArg(ARG_FTP_PORT, true, false, "FTP server port to connect", new long[][]{new long[]{1024, Character.MAX_VALUE}}),
			new IntegerArg(ARG_FTP_DATA_PORT, false, "Fixed FTP data port number to transmit content. If not typed or zero, any scratch port will be used", 0, new long[][]{new long[]{1024, Character.MAX_VALUE}}),
			new StringArg(ARG_ANON_USER, false, "Default 'user/password' to login to FTP server. If missing, internal validator will be used", ""),
			new FileArg(ARG_FTP_ROOT, true, true, "Root directory for FTP server users"),
			new BooleanArg(ARG_JMX_ENABLE, false, "Turn on JMX to control the service", false),
			new BooleanArg(ARG_DEBUG_TRACE, false, "Turn on debug trace on stderr", false)
		};
		
		private ApplicationArgParser() {
			super(KEYS);
		}
		
		@Override
		protected String finalValidation(final ArgParser parser) throws CommandLineParametersException {
			final String	result = super.finalValidation(parser);
			
			if (result == null) {
				final File		f = parser.getValue(ARG_FTP_ROOT, File.class);
				
				if (!(f.exists() && f.isDirectory() && f.canRead())) {
					return "Root directory ["+f.getAbsolutePath()+"] not exists, not a directory or has not grants to access for you";
				}
				final String	userPass = parser.getValue(ARG_ANON_USER, String.class);
				
				if (!userPass.matches("([a-zA-Z0-9])+/([a-zA-Z0-9])+") ) {
					return "Illegal 'user/password' string format";
				}
				
				if (parser.getValue(ARG_FTP_PORT, int.class).equals(parser.getValue(ARG_FTP_DATA_PORT, int.class))) {
					return "FTP port ["+parser.getValue(ARG_FTP_PORT, int.class)+"] and FTP data port ["+parser.getValue(ARG_FTP_DATA_PORT, int.class)+"] must be different";
				}
				return null;
			}
			else {
				return result;
			}
		}
	}
}
