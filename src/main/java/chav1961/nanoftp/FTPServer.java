package chav1961.nanoftp;

import java.io.File;
import java.io.IOException;

import chav1961.purelib.basic.interfaces.LoggerFacade;
import chav1961.purelib.basic.interfaces.LoggerFacadeOwner;
import chav1961.purelib.concurrent.interfaces.ExecutionControl;

public class FTPServer implements ExecutionControl, LoggerFacadeOwner, AutoCloseable {
	public FTPServer(final LoggerFacade logger, final int serverPort, final int dataPort, final File root, final SimpleValidator validator, final boolean needDebug) {
		
	}

	@Override
	public LoggerFacade getLogger() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void start() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void suspend() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resume() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isStarted() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSuspended() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void shutdown() {
		
	}
}
