/*      PANDA -- a simple transaction monitor

Copyright (C) 1998-1999 Ogochan.
              2000-2003 Ogochan & JMA (Japan Medical Association).

This module is part of PANDA.

		PANDA is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY.  No author or distributor accepts responsibility
to anyone for the consequences of using it or for whether it serves
any particular purpose or works at all, unless he says so in writing.
Refer to the GNU General Public License for full details.

		Everyone is granted permission to copy, modify and redistribute
PANDA, but only under the conditions described in the GNU General
Public License.  A copy of this license is supposed to have been given
to you along with PANDA so you can know your rights and
responsibilities.  It should be in a file named COPYING.  Among other
things, the copyright notice and this notice must be preserved on all
copies.
*/

package org.montsuqi.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.montsuqi.monsia.Style;
import org.montsuqi.util.Logger;
import org.montsuqi.util.OptionParser;
import org.montsuqi.util.SystemEnvironment;

public class Client implements Runnable {

	private Configuration conf;
	private Logger logger;
	private Protocol protocol;

	private static final String CLIENT_VERSION = "0.0"; //$NON-NLS-1$

	public Client(Configuration conf) {
		this.conf = conf;
		logger = Logger.getLogger(Client.class);
	}

	private static Client parseCommandLine(String[] args) {
		OptionParser options = new OptionParser();
		options.add("port", Messages.getString("Client.port_number"), Configuration.DEFAULT_PORT); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("host", Messages.getString("Client.host_name"), Configuration.DEFAULT_HOST); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("cache", Messages.getString("Client.cache_directory"), Configuration.DEFAULT_CACHE_PATH); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("user", Messages.getString("Client.user_name"), Configuration.DEFAULT_USER); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("pass", Messages.getString("Client.password"), ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		options.add("encoding", Messages.getString("Client.server_character_encoding"), Configuration.DEFAULT_ENCODING); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("style", Messages.getString("Client.styles"), Configuration.DEFAULT_STYLES); //$NON-NLS-1$ //$NON-NLS-2$
		int protocolVersion = Configuration.DEFAULT_PROTOCOL_VERSION;
		options.add("v1", Messages.getString("Client.use_protocol_version_1"), protocolVersion == 1); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("v2", Messages.getString("Client.use_protocol_version_2"), protocolVersion == 2); //$NON-NLS-1$ //$NON-NLS-2$
		options.add("useSSL", "SSL", Configuration.DEFAULT_USE_SSL); //$NON-NLS-1$ //$NON-NLS-2$

		String[] files = options.parse(Client.class.getName(), args);
		
		Configuration conf = new Configuration(Client.class);
		conf.setPort(options.getInt("port")); //$NON-NLS-1$
		conf.setHost(options.getString("host")); //$NON-NLS-1$
		conf.setCache(options.getString("cache")); //$NON-NLS-1$
		conf.setUser(options.getString("user")); //$NON-NLS-1$
		conf.setPass(options.getString("pass")); //$NON-NLS-1$
		conf.setEncoding(options.getString("encoding")); //$NON-NLS-1$
		conf.setStyleFileName(options.getString("style")); //$NON-NLS-1$

		boolean v1 = options.getBoolean("v1"); //$NON-NLS-1$
		boolean v2 = options.getBoolean("v2"); //$NON-NLS-1$
		if ( ! (v1 ^ v2)) {
			throw new IllegalArgumentException("specify -v1 or -v2, not both."); //$NON-NLS-1$
		}
		if (v1) {
			conf.setProtocolVersion(1);
		} else if (v2) {
			conf.setProtocolVersion(2);
		} else {
			assert false : "-v1 or -v2 should have been given."; //$NON-NLS-1$
		}

		conf.setUseSSL(options.getBoolean("useSSL")); //$NON-NLS-1$

		conf.setApplication(files.length > 0 ? files[0] : null);

		return new Client(conf);
	}

	void connect() throws IOException {
		String encoding = conf.getEncoding();
		Map styles = loadStyles();
		String[] pathElements = {
			conf.getCache(),
			conf.getHost(),
			String.valueOf(conf.getPort())
		};
		File cacheRoot = SystemEnvironment.createFilePath(pathElements);
		int protocolVersion = conf.getProtocolVersion();
		protocol = new Protocol(this, encoding, styles, cacheRoot, protocolVersion);

		String user = conf.getUser();
		String password = conf.getPass();
		String application = conf.getApplication();
		protocol.sendConnect(user, password, application);
	}

	private Map loadStyles() {
		URL url = conf.getStyleURL();
		try {
			logger.info("loading styles from URL: {0}", url); //$NON-NLS-1$
			InputStream in = url.openStream();
			return Style.load(in);
		} catch (IOException e) {
			logger.debug(e);
			logger.info("using empty style set"); //$NON-NLS-1$
			return Collections.EMPTY_MAP;
		}
	}

	Socket createSocket() throws IOException {
		String host = conf.getHost();
		int port = conf.getPort();
		SocketAddress address = new InetSocketAddress(host, port);
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.connect(address);
		Socket socket = socketChannel.socket();
		if ( ! conf.getUseSSL()) {
			return socket;
		}
		try {
			return createSSLSocket(host, port, socket);
		} catch (GeneralSecurityException e) {
			IOException ioe = new IOException();
			ioe.initCause(e);
			throw ioe;
		}
	}

	private Socket createSSLSocket(String host, int port, Socket socket) throws GeneralSecurityException, IOException {
		TrustManager[] tms = getTrustManagers();
		KeyManager[] kms = getKeyManagers();
		SSLContext ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$
		ctx.init(kms, tms, null);
		SSLSocketFactory factory = ctx.getSocketFactory();
		return factory.createSocket(socket, host, port, true);
	}

	private TrustManager[] getTrustManagers() throws GeneralSecurityException, IOException {
		String fileName = conf.getServerCertificateFileName();
		if (fileName.length() == 0) {
			return null;
		}
		CertificateFactory cf = CertificateFactory.getInstance("X509"); //$NON-NLS-1$
		Certificate cert = cf.generateCertificate(new FileInputStream(fileName));
		KeyStore ks = KeyStore.getInstance("JKS"); //$NON-NLS-1$
		ks.load(null, null);
		ks.setCertificateEntry(conf.getClientCertificateAlias(), cert); //$NON-NLS-1$
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509"); //$NON-NLS-1$
		tmf.init(ks);
		return tmf.getTrustManagers();
	}

	private KeyManager[] getKeyManagers() throws GeneralSecurityException, IOException {
		String fileName = conf.getClientCertificateFileName();
		if (fileName.length() == 0) {
			return null;
		}
		char[] pass = conf.getClientCertificatePass().toCharArray();
		KeyStore ks = KeyStore.getInstance("PKCS12"); //$NON-NLS-1$
		ks.load(new FileInputStream(fileName), pass);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); //$NON-NLS-1$
		kmf.init(ks, pass);
		return kmf.getKeyManagers();
	}

	public void run() {
		try {
			protocol.checkScreens(true);
			protocol.getScreenData();
		} catch (IOException e) {
			logger.fatal(e);
		}
	}

	void exitSystem() {
		try {
			synchronized (this) {
				protocol.sendPacketClass(PacketClass.END);
			}
		} catch (Exception e) {
			logger.warn(e);
		} finally {
			System.exit(0);
		}
	}

	public void finalize() {
		if (protocol != null) {
			exitSystem();
		}
	}

	public static void main(String[] args) {
		Object[] params = { CLIENT_VERSION };
		System.out.println(MessageFormat.format(Messages.getString("Client.banner_format"), params)); //$NON-NLS-1$

		Client client = Client.parseCommandLine(args);
		try {
			client.connect();
			Thread t = new Thread(client);
			t.start();
		} catch (Exception e) {
			throw new Error(e);
		}
	}
}
