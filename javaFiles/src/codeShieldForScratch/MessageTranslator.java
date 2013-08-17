/*
 * MessageTranslator.java
 * 
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
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * This file translates json strings between Scratch and Arduino
 */
public class MessageTranslator {

    // global values
    public static String COMMPORT = "/dev/ttyACM0"; // arduino com port
    public static int Port = 50207; // tcp port number must match value in json
    // file for scratch
    
    // Arduino pin numbers as assigned by CodeShield
    // CodeShield reporters
    public static final int POTENTIOMETER = 2;  // shared with relay
    public static final int HALL_EFFECT = 3;  // shared with PIEZO
    public static final int THERMISTOR = 4;
    public static final int PHOTO_CELL = 5;    // shared with servo
    public static final int PUSH_BUTTON = 12;
    public static final int SLIDE_SWITCH = 13;
    public static final int ENCODER = 14;
    // CodeShield sensors
    public static final int RELAY = 2;  // shared with POTENTIOMETER
    public static final int PIEZO = 3;  // shared with HALL_EFFECT
    public static final int SERVO = 5;  // shared with PHOTO_CELL
    public static final int WHITE_LED = 6;
    public static final int RGB_BLUE = 9;
    public static final int RGB_GREEN = 10;
    public static final int RGB_RED = 11;
    public static final int NUM_SENSORS = 7;
    public static final int MAX_NUM_SENSORS = 16; // theoretical
    // LED Colors - in some cases created by mixing different colors
    // selection values on scratch control block

    public static final int RED = 1;
    public static final int GREEN = 2;
    public static final int BLUE = 3;
    public static final int WHITE = 4;
    public static final int INDIGO = 5;
    public static final int ORANGE = 6;
    public static final int YELLOW = 7;
    public static final int VIOLET = 8;
    
    // type of LED control - PWM or Digital
    public static final int LEDDIGITAL = 0;
    public static final int LEDPWM = 1;
    private boolean onlyDigitalLedWrite = false;
    SerialManager serManager;  // arduino comm interface
    static int counter = 0;    // polling counter to
    // throttle the number of polls sent by
    // Scratch
    InputStream sockIn;        // streams to and from Scratch
    OutputStream sockOut;
    Socket scratchSocket;      // the actual socket 
    static int reporterIndex = 0; // index into the poll string array
    // we only send one poll item at a time
    String[] pollStrings;         // array of poll strings
    // array to save previous sensor readings
    static int[] sensorReadings = new int[MAX_NUM_SENSORS];

    public MessageTranslator(SerialManager serManager, Socket scratchSocket) {

        // an array of json strings to query the Arduino to satisfy the Scratch
        // poll request
        this.pollStrings = new String[]{
            "{\"read\":{\"pin\":2,\"type\":\"analog\"}}",
            "{\"read\":{\"pin\":3,\"type\":\"analog\"}}",
            "{\"read\":{\"pin\":4,\"type\":\"analog\"}}",
            "{\"read\":{\"pin\":5,\"type\":\"analog\"}}",
            "{\"read\":{\"pin\":12,\"type\":\"digital\"}}",
            "{\"read\":{\"pin\":13,\"type\":\"digital\"}}",
            "{\"read\":{\"encoder\":0}}"
        };
        try {
            // initialize the sensor readings array
            for (int i = 0; i < MAX_NUM_SENSORS; i++) {
                sensorReadings[i] = 0;
            }
            this.serManager = serManager;
            this.scratchSocket = scratchSocket;
            sockIn = this.scratchSocket.getInputStream();
            sockOut = this.scratchSocket.getOutputStream();
        } catch (IOException ex) {
            Logger.getLogger(MessageTranslator.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0);
        }
    }

    // handle messages from Scratch
    // 
    public void handleMsg(String msg) throws Exception {
        // Handle a command.
        String arduinoJSON;       // string to hold json translation to arduino
        String ardReplyString;    // json reply string back from arduino
        String toScratch;         // string to send to scratch
        int ledSelection;         // led selected by command
        int ledIntensity;         // led intensity
        int piezoFreq;            // piezo frequency
        int duration;             // piezo duration
        int servoDegrees;         // servo position in degrees 0-180
        int relayState;           // relay state
        JSONArray paramsArray;    // json parameters array
        JSONObject msgObj = new JSONObject(msg); // json object

        // look for keyword method in json string to grab json operation
        String operation = (String) msgObj.get("method");

        // get operation 

        // got a poll request
        if (operation.equals("poll")) {
            // skip every other poll to allow time for processing
            counter++;
            if (counter > 2) {
                counter = 0;

                // send a poll request to the arduino
                serManager.writeToArduino(pollStrings[reporterIndex++]);

                // wait for reply from ardino
                ardReplyString = serManager.getReply();

                // convert to scratch json format
                toScratch = convertAJSONtoSJSON(ardReplyString, reporterIndex);


                // if the value has not changed, a null string is returned and
                // we will ignore and carry on
                if (!toScratch.equals("")) {
                    // send JSON reply string to scratch
                    byte[] outBuf = toScratch.getBytes();
                    sockOut.write(outBuf, 0, outBuf.length);
                    sockOut.flush();
                }
                if (reporterIndex == NUM_SENSORS) {
                    reporterIndex = 0;
                }
            }
        } else { // not a poll but a command 

            ardReplyString = "";
            switch (operation) {
                // handle LED command for PWM control
                case "LEDSelect":
                    // get the parameters JSONArray
                    paramsArray = msgObj.getJSONArray("params");
                    // only one element in the array - get the value
                    ledSelection = (int) paramsArray.getInt(0);
                    ledIntensity = (int) paramsArray.getInt(1);
                    selectLED(ledSelection, ledIntensity, LEDPWM);
                    break;
                // handle LED command for digital control
                case "LEDDigitalSelect":
                    // get the parameters JSONArray
                    paramsArray = msgObj.getJSONArray("params");
                    // only one element in the array - get the value
                    ledSelection = (int) paramsArray.getInt(0);
                    ledIntensity = (int) paramsArray.getInt(1);
                    selectLED(ledSelection, ledIntensity, LEDDIGITAL);
                    break;
                // handle a piezo (sound) command
                case "piezoTone": {
                    paramsArray = msgObj.getJSONArray("params");
                    piezoFreq = (int) paramsArray.getInt(0);
                    duration = (int) paramsArray.getInt(1);
                    arduinoJSON = piezoTone(piezoFreq, duration);
                    serManager.writeToArduino(arduinoJSON);
                    ardReplyString = serManager.getReply();
                    break;
                }
                // handle a servo command
                case "servoDegrees": {
                    // servo library has a bug in controlling pwm for
                    // pins 9 & 10, so we have a workaround for the LEDS
                    onlyDigitalLedWrite = true;
                    paramsArray = msgObj.getJSONArray("params");
                    servoDegrees = (int) paramsArray.getInt(0);
                    arduinoJSON = servo(servoDegrees);
                    serManager.writeToArduino(arduinoJSON);
                    ardReplyString = serManager.getReply();
                    break;
                }
                // handle a relay command
                case "relayState": {
                    paramsArray = msgObj.getJSONArray("params");
                    relayState = (int) paramsArray.getInt(0);
                    arduinoJSON = relay(relayState);
                    serManager.writeToArduino(arduinoJSON);
                    ardReplyString = serManager.getReply();
                    break;
                }
                default: {
                    System.out.println("Unknown Operation" + operation);
                }
            }

            if( (!operation.equals("LEDSelect")) && (!operation.equals("LEDDigitalSelect"))) {
                if (!(ardReplyString.equals("{}\n"))) {
                    System.out.println("unexpected reply from arduino: " + ardReplyString);
                }
            }
        }
    }

    // process the led command
    void selectLED(int ledSelection, int ledIntensity, int type) {
        switch (ledSelection) {
            case WHITE:
                writeLed(WHITE_LED, ledIntensity, type);
                break;
            case RED:
                writeLed(RGB_RED, ledIntensity, type);
                break;
            case GREEN:
                writeLed(RGB_GREEN, ledIntensity, type);
                break;
            case BLUE:
                writeLed(RGB_BLUE, ledIntensity, type);
                break;

            case ORANGE:
                // turn off all 3 leds
                rgbAllOff();

                if (ledIntensity != 0) {
                    writeLed(RGB_RED, 255, type);
                    writeLed(RGB_GREEN, 128, type);
                }
                break;
            case YELLOW:
                rgbAllOff();

                if (ledIntensity != 0) {
                    writeLed(RGB_RED, 255, type);
                    writeLed(RGB_GREEN, 255, type);
                }
                break;

            case INDIGO:
                rgbAllOff();

                if (ledIntensity != 0) {
                    writeLed(RGB_RED, 128, type);
                    writeLed(RGB_GREEN, 128, type);
                    writeLed(RGB_BLUE, 255, type);
                }
                break;
            case VIOLET:
                rgbAllOff();

                if (ledIntensity != 0) {
                    writeLed(RGB_RED, 180, type);
                    writeLed(RGB_BLUE, 255, type);
                }
                break;
        }
    }

    // turn all rgb leds off
    void rgbAllOff() {
        writeLed(RGB_BLUE, 0, LEDDIGITAL);
        writeLed(RGB_GREEN, 0, LEDDIGITAL);
        writeLed(RGB_RED, 0, LEDDIGITAL);
    }

    // send the led command to the arduino
    void writeLed(int led, int intensity, int type) {

        String ardReplyString = "";
        // convert pin # to string
        String pinString = String.valueOf(led);

        // string that hold partial json commands
        String ledA;
        String ledB;
        String ledC;

        // json string to be sent to arduino
        String writeToArduinoString;

        // convert intensity value to a string
        String intensityString;

        if (onlyDigitalLedWrite) {
            type = LEDDIGITAL;
        }
        switch (type) {
            case LEDPWM:
                // guard max value
                if (intensity >= 255) {
                    intensity = 254;
                }

                intensityString = String.valueOf(intensity);
                // common string parts
                ledA = "{\"write\":{\"pin\":";
                ledB = ",\"type\":\"analog\",\"value\":";
                ledC = "}}";

                writeToArduinoString = ledA + pinString + ledB
                        + intensityString + ledC;

                serManager.writeToArduino(writeToArduinoString);
                ardReplyString = serManager.getReply();
                if (!(ardReplyString.equals("{}\n"))) {
                    System.out.println("writeLED unexpected reply from arduino: "
                            + ardReplyString + " for pin" + pinString
                            + " at intensity: " + intensityString);
                }
                break;
            default: // digital is default
                // convert intensity value to a string
                if (intensity > 1) {
                    intensity = 1;
                }
                if (intensity < 1) {
                    intensity = 0;
                }
                intensityString = String.valueOf(intensity);

                // common string parts
                ledA = "{\"write\":{\"pin\":";
                ledB = ",\"type\":\"digital\",\"value\":";
                ledC = "}}";

                writeToArduinoString = ledA + pinString + ledB
                        + intensityString + ledC;

                serManager.writeToArduino(writeToArduinoString);
                ardReplyString = serManager.getReply();
                if (!(ardReplyString.equals("{}\n"))) {
                    System.out.println("writeLED unexpected reply from arduino: "
                            + ardReplyString + " for pin" + pinString
                            + " at intensity: " + intensityString);
                }
                break;
        }
    }

    // initialize the arduino ports
    public boolean initArduino() {
        System.out.println("start init");

        String validator;
        String initString[];

        initString = new String[200];

        for (int i = 0; i < 200; i++) {
            initString[i] = "";
        }



        // initialize arduino inputs for CodeShield   
        // pot
        initString[0] = "{\"mode\":{\"mode\":\"input\",\"pin\":2}}";
        // thermistor
        initString[1] = "{\"mode\":{\"mode\":\"input\",\"pin\":4}}";
        // photocell
        initString[2] = "{\"mode\":{\"mode\":\"input\",\"pin\":5}}";
        // push button
        initString[3] = "{\"mode\":{\"mode\":\"input\",\"pin\":12}}";
        // slide switch
        initString[4] = "{\"mode\":{\"mode\":\"input\",\"pin\":13}}";

        // encoder a
        initString[5] = "{\"mode\":{\"mode\":\"input\",\"pin\":14}}";

        initString[6] = "{\"write\":{\"pin\":14,\"type\":\"digital\", \"value\":1}}";

        // encoder b
        initString[7] = "{\"mode\":{\"mode\":\"input\",\"pin\":15}}";
        initString[8] = "{\"write\":{\"pin\":15,\"type\":\"digital\", \"value\":1}}";


        // initialize arduino outputs for CodeShield and set them low
        // piezo
        initString[9] = "{\"mode\":{\"mode\":\"output\",\"pin\":3}}";
        initString[10] = "{\"write\":{\"pin\":3,\"type\":\"digital\", \"value\":0}}";
        // white led
        initString[11] = "{\"mode\":{\"mode\":\"output\",\"pin\":6}}";
        initString[12] = "{\"write\":{\"pin\":6,\"type\":\"digital\", \"value\":0}}";
        // rgb blue
        initString[13] = "{\"mode\":{\"mode\":\"output\",\"pin\":9}}";
        initString[14] = "{\"write\":{\"pin\":9,\"type\":\"digital\", \"value\":0}}";
        // rgb green
        initString[15] = "{\"mode\":{\"mode\":\"output\",\"pin\":10}}";
        initString[16] = "{\"write\":{\"pin\":10,\"type\":\"digital\", \"value\":0}}";
        // rgb red
        initString[17] = "{\"mode\":{\"mode\":\"output\",\"pin\":11}}";
        initString[18] = "{\"write\":{\"pin\":11,\"type\":\"digital\", \"value\":0}}";
        initString[19] = "";

        // send all initialization string to arduino
        // and validate reply
        int i ;
        for ( i = 0;
                !(initString[i].equals(
                ""));
                i++) {
            serManager.writeToArduino(initString[i]);
            validator = serManager.getReply();

            if (validator.equals("{}\n")) {
                continue;
            } else {
                return false;
            }
        }
        System.out.println("end init - initialized "+ i + " items");

        return true;
    }

    // handle the piezo command
    String piezoTone(int freq, int duration) {
        String rString = "{\"write\":{\"type\":\"piezo\", \"value\":";
        rString += String.valueOf(freq);
        rString += "\"pin\":";
        rString += String.valueOf(duration);
        rString += "}}";
        return rString;
    }

    // handle the servo command
    String servo(int servoDegrees) {
        String pre = "{\"write\":{\"type\":\"servo\", \"pin\":5\"value\":";
        String servoMotion = String.valueOf(servoDegrees);
        pre += servoMotion;
        pre += "}}";
        return pre;
    }

    // handle the relay command
    String relay(int relayState) {
        if (relayState != 0) {
            relayState = 1;
        }
        String pre = "{\"write\":{\"type\":\"digital\", \"pin\":2\"value\":";
        pre += String.valueOf(relayState);
        pre += "}}";
        return pre;
    }

    // convert arduino json string to scratch json string
    String convertAJSONtoSJSON(String ardReply, int replyIndex) {

        JSONObject fromAmsgObj;     // from arduino
        JSONObject toSmsgObj;       // to scratch
        JSONArray jArray;           // working array
        JSONArray outer, outer2;    // for nested arrays
        fromAmsgObj = new JSONObject(ardReply);  // arduino reply
        toSmsgObj = new JSONObject(); // to scratch
        jArray = new JSONArray();     // working array
        outer = new JSONArray();      // nested working arrays
        outer2 = new JSONArray();
        String scratchType = "";     // scratch command id
        String scrReply;             // scratch reply string

        JSONObject info;

        // extract the pin and pin value from the json string
        info = fromAmsgObj.getJSONObject("pinValue");
        int pinValue = info.getInt("value");
        int pin = info.getInt("pin");

        // save the new readings from arduino to report back to scratch
        sensorReadings[pin] = pinValue;
        String type;
        type = info.getString("type");

        // handle the reporters
        switch (pin) {
            case POTENTIOMETER:
                scratchType = "potVal";
                break;
            case HALL_EFFECT:
                scratchType = "hallVal";
                break;
            case THERMISTOR:
                scratchType = "thermVal";
                break;
            case PHOTO_CELL:
                scratchType = "photoVal";
                break;
            case PUSH_BUTTON:
                scratchType = "buttonVal";
                break;
            case SLIDE_SWITCH:
                scratchType = "switchVal";
                break;
            case ENCODER:
                scratchType = "encoderVal";
                break;
            default:
                System.out.println("Unknown pin value: " + pin);
                break;
        }

        // build the json output to send to scratch
        jArray = jArray.put(0, scratchType);

        jArray = jArray.put(1, pinValue);
        outer = outer.put(0, jArray);

        toSmsgObj = toSmsgObj.put("method", "update");
        // toSmsgObj = toSmsgObj.put("params", jArray) ;
        toSmsgObj = toSmsgObj.put("params", outer);
        //toSmsgObj = toSmsgObj.put("params", outer2) ;
        scrReply = toSmsgObj.toString();
        scrReply += "\n";
        //System.out.println("Reply String: "+ scrReply) ;
        return scrReply;
    }
}
