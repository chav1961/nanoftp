package chav1961.nanoftp;


import java.io.File;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import chav1961.nanoftp.internal.FTPServer;
import chav1961.nanoftp.internal.ModeList;
import chav1961.nanoftp.jmx.JmxManager;
import chav1961.nanoftp.jmx.JmxManagerMBean;
import chav1961.purelib.basic.ArgParser;
import chav1961.purelib.basic.exceptions.CommandLineParametersException;
import chav1961.purelib.basic.interfaces.LoggerFacade.Severity;

public class Application {
	public static final String	ARG_MODE = "mode";
	public static final String	ARG_FTP_PORT = "port";
	public static final String	ARG_FTP_DATA_PORT = "dataPort";
	public static final String	ARG_FTP_ROOT = "root";
	public static final String	ARG_ANON_USER = "user";
	public static final String	ARG_JMX_ENABLE = "jmx";
	public static final String	ARG_DEBUG_TRACE = "d";
	public static final String	JMX_NAME = "chav1961.nanoftp:type=basic,name=server";

	public static void main(String[] args) {
		final ArgParser	parser = new ApplicationArgParser();

		try {
			final ArgParser		parsed = parser.parse(args);
			final File			root = parsed.getValue(ARG_FTP_ROOT, File.class);
			final int			ftpPort = parsed.getValue(ARG_FTP_PORT, int.class);
			final int			ftpDataPort = parsed.getValue(ARG_FTP_DATA_PORT, int.class);
			final String		userPass = parsed.getValue(ARG_ANON_USER, String.class);
			final boolean		needDebug = parsed.getValue(ARG_DEBUG_TRACE, boolean.class);
			final ObjectName 	jmxName = new ObjectName(JMX_NAME);
			
			if (parsed.isTyped(ARG_MODE)) {
				final ModeList				mode = parsed.getValue(ARG_MODE, ModeList.class); 
				final VirtualMachine 		vm = getVM();
				final String 				jmxUrl = vm.startLocalManagementAgent();
				final JMXServiceURL 		url = new JMXServiceURL(jmxUrl);
				final JMXConnector 			connector = JMXConnectorFactory.connect(url);
				final MBeanServerConnection conn = connector.getMBeanServerConnection();				
				final JmxManagerMBean		mbeanProxy = JMX.newMBeanProxy(conn, jmxName, JmxManagerMBean.class, true);

				switch (mode) {
					case resume		:
						mbeanProxy.resume();
						break;
					case start		:
						mbeanProxy.start();
						break;
					case stop		:
						mbeanProxy.stop();
						break;
					case suspend	:
						mbeanProxy.suspend();
						break;
					case terminateAndExit	:
						mbeanProxy.terminateAndExit();
						break;
					default:
						throw new UnsupportedOperationException("Server mode ["+parsed.getValue(ARG_MODE, ModeList.class)+"] is not supported yet");
				}
				print("Command completed");
			}
			else {
				
				try(final FTPServer		server = new FTPServer(ftpPort, ftpDataPort, root, userPass, needDebug)) {
					final MBeanServer 	mBeanServer = ManagementFactory.getPlatformMBeanServer();

					Runtime.getRuntime().addShutdownHook(new Thread(()->{
						try {
							server.shutdown();
						} catch (IOException e) {
						}
					}));
					
					if (parsed.getValue(ARG_JMX_ENABLE, boolean.class)) {
						final JmxManager	mgr = new JmxManager(server);
						
						mBeanServer.registerMBean(mgr, jmxName);
						if (needDebug) {
							server.getLogger().message(Severity.debug, "JMX server started, JMX name is ["+JMX_NAME+"]");
						}
					}

					if (needDebug) {
						server.getLogger().message(Severity.info, "Nano FTP server listen on [%1$d] port", ftpPort);
					}

					server.run();
					
					if (parsed.getValue(ARG_JMX_ENABLE, boolean.class)) {
						mBeanServer.unregisterMBean(jmxName);
						if (needDebug) {
							server.getLogger().message(Severity.debug, "JMX server stopped");
						}
					}
					if (needDebug) {
						server.getLogger().message(Severity.info, "Nano FTP server stopped");
					}
				}
			}
		} catch (CommandLineParametersException e) {
			print(e.getLocalizedMessage());
			print(parser.getUsage("nanoftp"));
			System.exit(128);
		} catch (IOException | MalformedObjectNameException | InstanceAlreadyExistsException | InstanceNotFoundException |MBeanRegistrationException | NotCompliantMBeanException | AttachNotSupportedException e) {
			e.printStackTrace();
			System.exit(129);
		}
	}

	private static VirtualMachine getVM() throws AttachNotSupportedException, IOException {
		final String	name = Application.class.getProtectionDomain().getCodeSource().getLocation().toString();
		final String	tail = name.substring(name.lastIndexOf('/')+1);
				
		for(VirtualMachineDescriptor item : VirtualMachine.list()) {
			if (item.displayName().startsWith(tail) && !modeListPresents(item.displayName())) {
				return VirtualMachine.attach(item);
			}
		}
		throw new AttachNotSupportedException("No any nanoftp servers detected in this computer");
	}

	private static boolean modeListPresents(final String displayName) {
		for(ModeList item : ModeList.values()) {
			if (displayName.contains(" "+item.name()+" ")) {
				return true;
			}
		}
		return false;
	}

	private static void print(final String message) {
		System.err.println(message);
	}
	
	private static class ApplicationArgParser extends ArgParser {
		private static final ArgParser.AbstractArg[]	KEYS = {
			new EnumArg<ModeList>(ARG_MODE, ModeList.class, false, true, "Server control mode. Can be used after server startup only. To startup server, do not type this argument"),
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
					return "Illegal 'user/password' string format. User/password must be splitten by '/' and can contain(s) only letters or digits in the name and/or password";
				}

				if (parser.getValue(ARG_FTP_PORT, int.class) > Character.MAX_VALUE || parser.getValue(ARG_FTP_PORT, int.class) < 0) {
					return "FTP port ["+parser.getValue(ARG_FTP_PORT, int.class)+"] out of range 0.."+(int)Character.MAX_VALUE;
				}
				if (parser.getValue(ARG_FTP_DATA_PORT, int.class) > Character.MAX_VALUE || parser.getValue(ARG_FTP_DATA_PORT, int.class) < 0) {
					return "FTP data port ["+parser.getValue(ARG_FTP_DATA_PORT, int.class)+"] out of range 0.."+(int)Character.MAX_VALUE;
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
