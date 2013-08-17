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

import codeShieldForScratch.SerialManager.*;

/**
 * Scratch Hardware Extension to communicate with Arduino
 *
 * @author afy
 */
public class ScratchArduinoExtension {

    /**
     * @param args the command line arguments
     *
     * The main class for the application
     */


    public static void main(String[] args) {


        // capture the comport if given
        String firstArg;
        if (args.length > 0) {
            MessageTranslator.COMMPORT = args[0];
            System.out.println("Comport = " + MessageTranslator.COMMPORT);
        }

        // create the serial manager

        // create the serial manager
        boolean rVal;

        SerialManager serialManager = new SerialManager(MessageTranslator.COMMPORT);

        // open the serial port
        rVal = serialManager.open();

        // open the TCP Server Socket
        // the server manager will instantiate the message handler
        // in its own thread
        TCPServerManager tsm;
        tsm = new TCPServerManager(MessageTranslator.Port, serialManager);
        tsm.openSocketServer();
    }
}
