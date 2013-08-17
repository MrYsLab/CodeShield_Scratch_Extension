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

import java.util.logging.Level;
import java.util.logging.Logger;
import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

/**
 * This class manages the serial port to communicate with the Arduino
 *
 * @author afy
 */
public class SerialManager {

    String comPort;
    SerialPort serialPort;

    // constructor
    SerialManager(String comPort) {

        this.comPort = comPort;
    }

    /**
     * Open the serial port and set its parameters
     *
     * @return
     */
    public boolean open() {

        // create a new serial port object
        serialPort = new SerialPort(comPort);

        // reply string from Arduino json
        String jsonReply = "";

        // return variable
        boolean returnValue = false;
        // try opening the serial port

        try {
            returnValue = serialPort.openPort();//Open serial port

            // set the port parameters
            if (returnValue == true) {
                returnValue = serialPort.setParams(SerialPort.BAUDRATE_57600,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
            }

            // set events mask
            if (false == (returnValue =
                    serialPort.setEventsMask(SerialPort.MASK_TXEMPTY + SerialPort.MASK_RXCHAR))) {
                serialPort.closePort();
                returnValue = false;
            }

            // purge the serial port buffers of any junk
            if (returnValue == true) {
                returnValue = serialPort.purgePort(SerialPort.PURGE_RXCLEAR
                        | SerialPort.PURGE_TXCLEAR);
            }

        } catch (SerialPortException ex) {
            System.out.println(ex);
            System.out.println("Is this the correct serial port?");
            System.exit(0);
        }

        // get reply from Arduino json
        jsonReply = getReply();

        // make sure we are really talking
        if (jsonReply.equals("{\"status\":\"ready\"}\r\n")) {
            System.out.println("Serial Port Opened");
        } else {
            System.out.println("Incorrect reply string: " + jsonReply);
            returnValue = false;
        }
        return returnValue;
    }

    // write a string to the Arduino
    /**
     *
     * @param toArduino
     */
    public void writeToArduino(String toArduino) {

        String arduinoReply;
        arduinoReply = "";
        int transmitState = 0;

        try {
            boolean closePort; // close port return in case of error     
            boolean writeBytes; //Write data to port

            // wait for transmitter to be empty

            while (transmitState == 0) {
                transmitState =
                        serialPort.getEventsMask();

                transmitState &= SerialPort.MASK_TXEMPTY;

            }
            writeBytes = serialPort.writeBytes(toArduino.getBytes());

        } catch (SerialPortException ex1) {
            Logger.getLogger(SerialManager.class.getName()).log(Level.SEVERE, null, ex1);
            System.exit(0);
        }
    }

    /**
     * // Wait for a json reply string from Arduino // Reply strings are
     * expected to be "/n" terminated.
     *
     * @return
     */
    public String getReply() {


        int receiveState = 0;
        byte[] recvdBytes; // bytes received
        byte[] oneByte;
        int byteCounter = 0;


        recvdBytes = new byte[80];
        oneByte = new byte[80];

        // initialize array
        for (int i = 0; i < 80; i++) {
            recvdBytes[i] = 0;
        }

        /* wait for reply */
        oneByte[0] = 0;

        // keep collecting data until newline is received
        while ((oneByte[0] != ('\n'))) {
            try {

                while (receiveState == 0) {
                    receiveState =
                            serialPort.getEventsMask();
                    receiveState &= SerialPort.MASK_RXCHAR;
                }

                // wait up until 20 seconds for data
                // when we get data, put it into the buffer
                oneByte = serialPort.readBytes(1, 20000);
                recvdBytes[byteCounter] = oneByte[0];
                byteCounter++;
                //arduinoReply += oneChar;

            } catch (SerialPortException | SerialPortTimeoutException ex) {
                Logger.getLogger(SerialManager.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(0);
            }
        }
        // put the bytes into string format
        String arduinoReply;
        arduinoReply = new String(recvdBytes, 0, byteCounter);

        char[] charArray;
        charArray = arduinoReply.toCharArray();

        // send the reply back to caller
        return arduinoReply;
    }

    // close the serial port
    public void closeSerial() {
        try {
            serialPort.closePort();
        } catch (SerialPortException ex) {
            Logger.getLogger(SerialManager.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0);
        }
    }
}
