package chav1961.nanoftp.jmx;

import java.io.IOException;

import chav1961.nanoftp.FTPServer;

public class JmxManager implements JmxManagerMBean {
	private final FTPServer	server;
	
	public JmxManager(final FTPServer server) {
		if (server == null) {
			throw new NullPointerException("Server can't be null");
		}
		else {
			this.server = server;
		}
	}
	
	@Override
	public void start() throws IOException {
		try {
			getServer().start();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public void suspend() throws IOException {
		try {
			getServer().suspend();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public void resume() throws IOException {
		try {
			getServer().resume();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public void stop() throws IOException {
		try {
			getServer().stop();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public void terminateAndExit() throws IOException {
		try {
			getServer().shutdown();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean isStarted() {
		return getServer().isStarted();
	}

	@Override
	public boolean isSuspended() {
		return getServer().isSuspended();
	}
	
	private FTPServer getServer() {
		return server;
	}
}
