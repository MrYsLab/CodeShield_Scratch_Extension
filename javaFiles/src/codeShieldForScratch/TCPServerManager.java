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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class creates the Socket Server for Scratch TCP communications
 * @author afy
 */
//public class TCPServerManager {
public class TCPServerManager {

    int portNumber;
    SerialManager serialManager;        // serial interface to Arduino
    MessageManager messageManager;      // json message translator          

    // constructor
    public TCPServerManager(int portNumber, SerialManager serialManager) {

        this.portNumber = portNumber;
        this.serialManager = serialManager;
        System.out.println("TCPServerManager created");
    }

    /**
     *
     * @param portNumber
     * @param port
     * @return
     */

    // open the Socket Server so that Scratch can connect
    public void openSocketServer() {
        ServerSocket serverSock;
        serverSock = null;
        MessageManager msgManager = null ;
        Socket sock;

        try {
            InetAddress addr = InetAddress.getLocalHost();
            System.out.println("ScratchCodeShield started on "
                    + addr.toString());


            serverSock = new ServerSocket(portNumber);


            System.out.println("TCP Server Opened");
            while (true) {
                sock = serverSock.accept();
                if( msgManager == null)
                {
                    // create the message manager
                    msgManager = new MessageManager(serialManager, sock);
                    new Thread(msgManager).start();
                }
            }

        } catch (UnknownHostException ex) {
            Logger.getLogger(TCPServerManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TCPServerManager.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0) ;
        }
        //return serverSock;

    }

}
