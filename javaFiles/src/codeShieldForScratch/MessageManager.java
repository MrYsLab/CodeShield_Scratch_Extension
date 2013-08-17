/*
 Scratch 2.0 Hardware Extension for the CodeShield Arduino Interface.

 Written by Alan Yorinks
 Copyright (c) 2013 Alan Yorinks All right reserved.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.

 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
  
 Version .01 August 15, 2013
 */
package codeShieldForScratch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 *
 *
 * This class translates json messages to and from both scratch and arduino It
 * also takes care of initial handshaking with scratch
 *
 * @author afy
 */
public class MessageManager implements Runnable {

    public static final int PORT = 50207; // the ExtensionExample port number
    SerialManager serManager;  // arduino comm interface
    Socket scratchSocket;      // scratch TCP IP interface
    MessageTranslator mTranslator;     // user application class
    InputStream sockIn;        // streams to and from Scratch
    OutputStream sockOut;
    private String msgBuf = "";
    int port;

    // constructor
    public MessageManager(SerialManager serManager, Socket scratchSocket) {
        this.serManager = serManager;
        this.scratchSocket = scratchSocket;
        System.out.println("MessageManager Created");
    }

    // message handler thread
    @Override
    public void run() {
        try {
            try {
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                Logger.getLogger(MessageManager.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(0) ;
            }
            try {
                getScratchData();
            } catch (InterruptedException ex) {
                Logger.getLogger(MessageManager.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(0) ;
            }
        } catch (IOException ex) {
            Logger.getLogger(MessageManager.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0) ;
        }
    }

    /**
     * This method creates an instance of the MessageTranslator
     */
    public void getScratchData() throws IOException, InterruptedException {
        try {
            // tcp socket streams for input and output
            sockIn = scratchSocket.getInputStream();
            sockOut = scratchSocket.getOutputStream();

            // buffer to collect the data
            byte[] buf = new byte[5000];

            // create a message translator instance where the real work is done   
            mTranslator = new MessageTranslator(serManager, this.scratchSocket);
            if (mTranslator.initArduino() == false) {
                System.out.println("Arduino Init Failed");
                try {
                    serManager.closeSerial();
                    this.scratchSocket.close();
                } catch (IOException ex) {
                    Logger.getLogger(MessageManager.class.getName()).log(Level.SEVERE, null, ex);
                    System.exit(0);
                }
            }
            System.err.println("Scratch is connected");
            // collect the data from Scratch and pass it on to the translator
            while (true) {
                try {
                    int bytes_read = sockIn.read(buf, 0, buf.length);
                    if (bytes_read < 0) {
                        break; // client closed socket
                    }
                    String s = new String(Arrays.copyOf(buf, bytes_read));

                    // Flash policy stuff from Scratch - need to reply
                    if (s.equals("<policy-file-request/>\0")) {
                        // To support the Flash security model, the server 
                        // responds to a policy file request by sending a policy
                        //  file string that allows Flash to connect to 
                        //this port. The policy request and response are
                        //  terminated with null characters ('\0') rather than
                        // newlines. The policy exchange happens before 
                        //message exchange begins.
                        sendPolicyRequest(sockOut);
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException ex) {
                            System.out.println("Policy Reply Failed");
                            Logger.getLogger(MessageManager.class.getName()).log(Level.SEVERE, null, ex);
                            System.exit(0);
                        }
                    } else {
                        // here is where we collect the json strings from Scratch
                        msgBuf += s;
                        int i;
                        while ((i = msgBuf.indexOf('\n')) >= 0) {
                            // While msgBuf includes a newline, extract a message and handle it.
                            // If a message is not yet complete (no newline), msgBuf holds the partial
                            // message until more data arrives.
                            String msg = msgBuf.substring(0, i);
                            msgBuf = msgBuf.substring(i + 1, msgBuf.length());
                            try {
                                mTranslator.handleMsg(msg);
                            } catch (Exception e) {
                                // Errors while handling a message print the stack but do not kill the server.
                                System.err.println("problem handling: " + msg);
                                e.printStackTrace(System.err);
                                System.exit(0);;
                            }
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(MessageManager.class.getName()).log(Level.SEVERE, null, ex);
                    System.exit(0);
                }
            }
            scratchSocket.close();
            serManager.closeSerial();
            System.err.println("-----Closed-----");
            System.exit(0);
        } catch (IOException ex) {
            Logger.getLogger(MessageManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void sendPolicyRequest(OutputStream sockOut) throws IOException {
        // Send a Flash null-teriminated cross-domain policy file.
        System.out.println("Sending Policy");

        String policyFile =
                "<cross-domain-policy>\n"
                + "  <allow-access-from domain=\"*\" to-ports=\"" + PORT + "\"/>\n"
                + "</cross-domain-policy>\n\0";
        byte[] outBuf = policyFile.getBytes();
        sockOut.write(outBuf, 0, outBuf.length);
        sockOut.flush();
    }
}
