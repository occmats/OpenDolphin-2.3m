package org.montsuqi.client;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.text.MessageFormat;

import org.montsuqi.util.Logger;
import org.montsuqi.util.OptionParser;

public class Client implements Runnable {

	private boolean connected;
	private static final int PORT_GLTERM = 8000;
	private static final String CLIENT_VERSION = "0.0"; //$NON-NLS-1$

	private int portNumber;
	private String host;
	private String cache;
	private String user;
	private String pass;
	private String encoding;
	private String currentApplication;

	// if USE_SSL
	//private String key;
	//private String cert;
	private boolean useSSL;
	private boolean verify;
	//private String CApath;
	//private String CAfile;

	private Protocol protocol;
	private Logger logger;

	String getCacheFileName(String name) {
		String sep = System.getProperty("file.separator"); //$NON-NLS-1$
		StringBuffer buf = new StringBuffer();
		buf.append(cache);
		buf.append(sep);
		buf.append(host);
		buf.append(sep);
		buf.append(portNumber);
		buf.append(sep);
		buf.append(name);
		return buf.toString();
	}
	
	private String[] parseOptions(String[] args) {
		OptionParser options = new OptionParser();

		options.add("port", Messages.getString("Client.port_number"), PORT_GLTERM); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("host", Messages.getString("Client.host_name"), "localhost"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		options.add("cache", Messages.getString("Client.cache_directory"), "cache"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		options.add("user", Messages.getString("Client.user_name"), System.getProperty("user.name")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		options.add("pass", Messages.getString("Client.password"), ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		options.add("encoding", Messages.getString("Client.server_character_encoding"), "EUC-JP"); //$NON-LNS-1$
		options.add("v1", Messages.getString("Client.use_protocol_version_1"), true); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("v2", Messages.getString("Client.use_protocol_version_2"), true); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("useSSL", "SSL", false); //$NON-NLS-1$ //$NON-NLS-2$
		//options.add("key", "key file name(pem)", null);
		//options.add("cert", "certification file name(pem)", null);
		//options.add("ssl", "use SSL", false);
		options.add("verifypeer", Messages.getString("Client.verify_peer"), false); //$NON-NLS-1$ //$NON-NLS-2$
		//options.add("CApath", "path to CA", null);
		//options.add("CAfile", "CA file", null);

		String[] files = options.parse(Client.class.getName(), args);

		portNumber = ((Integer)options.getValue("port")).intValue(); //$NON-NLS-1$
		host = (String)options.getValue("host"); //$NON-NLS-1$
		cache = (String)options.getValue("cache"); //$NON-NLS-1$
		user = (String)options.getValue("user"); //$NON-NLS-1$
		pass = (String)options.getValue("pass"); //$NON-NLS-1$
		encoding = (String)options.getValue("encoding");

		useSSL = ((Boolean)options.getValue("useSSL")).booleanValue(); //$NON-NLS-1$

		if (useSSL) {
			//key = (String)options.getValue("key");
			//cert = (String)options.getValue("cert");
			//useSSL = ((Boolean)options.getValue("ssl")).booleanValue();
			verify = ((Boolean)options.getValue("verifypeer")).booleanValue(); //$NON-NLS-1$
			//CApath = options.getValue("CApath");
			//CAfile = options.getValue("CAfile");
		}

		return files;
	}

	private Client(String[] args) {
		logger = Logger.getLogger(Client.class);

		String[] files = parseOptions(args);

		if (files.length > 0) {
			currentApplication = files[0];
		} else {
			currentApplication = "demo"; //$NON-NLS-1$
		}
		
		Socket s = null;
		try {
			String factoryName;
			Object[] options;
			if (useSSL) {
				factoryName = "org.montsuqi.client.SSLSocketCreator";
				options = new Object[] { new Boolean(verify) };
			} else {
				factoryName = "org.montsuqi.client.SocketCreator";
				options = null;
			}
			Class clazz = Class.forName(factoryName);
			Class[] argTypes = new Class[] { String.class, Integer.TYPE, Object[].class };
			Method create = clazz.getDeclaredMethod("create", argTypes);
			s = (Socket)create.invoke(null, new Object[] { host, new Integer(portNumber), options });
			protocol = new Protocol(this, s, encoding);
			connected = true;
		} catch (Exception e) {
			logger.fatal(e);
			System.exit(0);
		}
		if (protocol == null) {
			logger.fatal(Messages.getString("Client.cannot_connect")); //$NON-NLS-1$
		}
	}
		

	public void run() {
		try {
			protocol.sendConnect(user, pass, currentApplication);
			connected = true;
			protocol.checkScreens(true);
			protocol.getScreenData();
			while (connected) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					// ignore
				}
			}
			protocol.close();
		} catch (IOException e) {
			logger.fatal(e);
		}
	}
		
	public static void main(String[] args) {
		Client client = new Client(args);
		client.showBannar();
		client.run();
		client.exitSystem();
	}

	public void finalize() {
		if (protocol != null) {
			exitSystem();
		}
	}
	
	void exitSystem() {
		try {
			synchronized (this) {
				protocol.sendPacketClass(PacketClass.END);
				connected = false;
			}
		} catch (SocketException e) {
			logger.warn(e);
		} catch (IOException e) {
			logger.warn(e);
		} finally {
			System.exit(0);
		}
	}

	private void showBannar() {
		String format = Messages.getString("Client.banner_format");
		Object[] args = new Object[] { CLIENT_VERSION };
		String banner = MessageFormat.format(format, args);
		System.out.println(banner);
	}
}
