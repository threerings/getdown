//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.Container;
import java.awt.Image;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.swing.JApplet;
import javax.swing.JPanel;

import netscape.javascript.JSObject;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Properties;

import static com.threerings.getdown.Log.log;

/**
 * An applet that can be used to launch a Getdown application (when signed and given privileges).
 */
public class GetdownApplet extends JApplet
    implements ImageLoader
{
    /**
     * Sets the JavaScript callback to invoke when a message is received from the launched app.
     * The callback should be a function that accepts a single string parameter (the received
     * message).
     */
    public synchronized void setMessageCallback (JSObject callback)
    {
        _messageCallback = callback;
    }

    /**
     * Attempts to send a message to the launched app.
     *
     * @return true if we succeeded in sending the message, false if the launched app has not (yet)
     * established a connection to Getdown, or the send failed.
     */
    public synchronized boolean sendMessage (String message)
    {
        if (_connectOut != null) {
            try {
                _connectOut.writeUTF(message);
                return true;
            } catch (IOException e) {
                log.warning("Error sending message to app.", "message", message, e);
            }
        }
        return false;
    }

    @Override // documentation inherited
    public void init ()
    {
        _config = new GetdownAppletConfig(this);

        try {

            try {
                // Check our permissions, download getdown.txt, etc.
                _config.init();
            } catch (Exception e) {
                _errmsg = e.getMessage();
            }

            List<Certificate> signers = new ArrayList<Certificate>();
            Certificate cert = loadCertificate("resource.crt");
            if (cert != null) {
                signers.add(cert);
            } else {
                // getSigners() returns all certificates used to sign this applet which may allow a
                // third party to insert a trusted certificate. This should be avoided.
                log.warning("No resource certificate found, falling back to class signers");
                for (Object signer : GetdownApplet.class.getSigners()) {
                    if (signer instanceof Certificate) {
                        signers.add((Certificate)signer);
                    }
                }
            }
            _getdown = new Getdown(_config.appdir, null, signers,
                                   _config.jvmargs, _config.appargs) {
                @Override
                protected Container createContainer () {
                    getContentPane().removeAll();
                    return getContentPane();
                }
                @Override
                protected RotatingBackgrounds getBackground () {
                    return _config.getBackgroundImages(GetdownApplet.this);
                }
                @Override
                protected void showContainer () {
                    ((JPanel)getContentPane()).revalidate();
                }
                @Override
                protected void disposeContainer () {
                    // nothing to do as we're in an applet
                }
                @Override
                protected boolean invokeDirect () {
                    return _config.invokeDirect;
                }
                @Override
                protected JApplet getApplet () {
                    return GetdownApplet.this;
                }
                @Override
                protected void showDocument (String url) {
                    try {
                        getAppletContext().showDocument(new URL(url), "_blank");
                    } catch (MalformedURLException e) {
                        log.warning("Invalid document url.", "url", url, e);
                    }
                }
                @Override
                protected void launch () {
                    // if so configured, create a server socket to listen
                    // for a connection from the app
                    if (_config.allowConnect) {
                        try {
                            startConnectServer();
                        } catch (IOException e) {
                            log.warning("Failed to start connect server.", e);
                        }
                    }
                    super.launch();
                }
                @Override
                protected void exit (int exitCode) {
                    _status.stopThrob();
                    _app.releaseLock();
                    _config.redirect();
                }
            };

            // set up our user interface
            _config.config(_getdown);
            _getdown.preInit();

        } catch (Exception e) {
            // assume that if we already encountered an error, that is the root cause that we want
            // to report back to the user
            if (_errmsg == null) {
                _errmsg = e.getMessage();
            }
            log.warning("init() failed.", e);
        }
    }

    /**
     * Attempts to start the server that will accept a connection from the launched app, allowing
     * it to exchange messages with the JavaScript context.
     */
    protected void startConnectServer ()
        throws IOException
    {
        // bind and set a property with the local port that will be passed through to the app
        _serverSocket = new ServerSocket(0, 0, InetAddress.getByName(null));
        String port = String.valueOf(_serverSocket.getLocalPort());
        log.info("Listening for connections from launched app.", "port", port);
        System.setProperty(Application.PROP_PASSTHROUGH_PREFIX + Properties.CONNECT_PORT, port);
        Thread thread = new Thread("ConnectServer") {
            @Override
            public void run () {
                while (true) {
                    try {
                        acceptConnection();
                    } catch (IOException e) {
                        if (!_serverSocket.isClosed()) {
                            log.warning("Error accepting connection.", e);
                        }
                        break;
                    }
                }
            }
            protected void acceptConnection () throws IOException {
                Socket socket = _serverSocket.accept();
                log.info("App connected.", "port", socket.getPort());
                DataInputStream connectIn = new DataInputStream(socket.getInputStream());
                synchronized (GetdownApplet.this) {
                    _connectOut = new DataOutputStream(socket.getOutputStream());
                }
                while (true) {
                    try {
                        String message = connectIn.readUTF();
                        synchronized (GetdownApplet.this) {
                            if (message.equals("CLOSE")) {
                                socket.close();
                                log.info("App closed connection.");
                                break;
                            } else if (_messageCallback != null) {
                                _messageCallback.call("call",
                                    new Object[] { _messageCallback, message });
                            }
                        }
                    } catch (IOException e) {
                        if (!socket.isClosed()) {
                            log.warning("Error reading message.", e);
                        }
                        break;
                    }
                }
                synchronized (GetdownApplet.this) {
                    _connectOut = null;
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    // implemented from ImageLoader
    public Image loadImage (String path)
    {
        try {
            return getImage(new URL(getDocumentBase(), path));
        } catch (MalformedURLException e) {
            log.warning("Failed to load background image", "path", path, e);
            return null;
        }
    }

    @Override // documentation inherited
    public void start ()
    {
        if (_errmsg != null) {
            _getdown.fail(_errmsg);
        } else {
            try {
                _getdown.start();
            } catch (Exception e) {
                log.warning("start() failed.", e);
            }
        }
    }

    @Override // documentation inherited
    public void stop ()
    {
        // Interrupt the getdown thread to tell it to kill its current downloading or verifying
        // before launching
        _getdown.interrupt();
        // release the lock if the applet window is closed or replaced
        _getdown._app.releaseLock();
    }

    @Override // documentation inherited
    public synchronized void destroy ()
    {
        if (_serverSocket != null) {
            try {
                _serverSocket.close();
            } catch (IOException e) {
                log.warning("Error closing server socket.", e);
            }
        }
        if (_connectOut != null) {
            try {
                _connectOut.writeUTF("CLOSE");
                _connectOut.close();
                log.info("Disconnected from app.");
            } catch (IOException e) {
                log.warning("Error closing connect socket/output stream.", e);
            }
        }
    }

    /**
     * Creates the specified file and writes the supplied contents to it.
     */
    protected boolean writeToFile (File tofile, String contents)
    {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(tofile));
            out.println(contents);
            out.close();
            return true;
        } catch (IOException ioe) {
            log.warning("Failed to create '" + tofile + "'.", ioe);
            return false;
        }
    }

    protected static Certificate loadCertificate (String path)
    {
        try {
            URL keyUrl = GetdownApplet.class.getClassLoader().getResource(path);
            if (keyUrl == null) {
                return null;
            }
            InputStream is = keyUrl.openStream();
            try {
                return CertificateFactory.getInstance("X.509").generateCertificate(is);
            } finally {
                is.close();
            }
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The Getdown configuration as pulled from the applet params */
    protected GetdownAppletConfig _config;

    /** Handles all the actual getting down. */
    protected Getdown _getdown;

    /** An error encountered during initialization. */
    protected String _errmsg;

    /** The message callback registered by JavaScript on the containing page, if any. */
    protected JSObject _messageCallback;

    /** The server socket on which we listen for connections, if any. */
    protected ServerSocket _serverSocket;

    /** The output stream to the launched app, if a connection has been established. */
    protected DataOutputStream _connectOut;
}
