package org.thdccx.freeswitch.esl;
import com.google.common.base.Throwables;
import org.freeswitch.esl.client.dptools.Execute;
import org.freeswitch.esl.client.dptools.ExecuteException;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.internal.Context;
import org.freeswitch.esl.client.outbound.IClientHandler;
import org.freeswitch.esl.client.outbound.SocketClient;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslHeaders.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public class Demo {
    private static Logger logger = LoggerFactory.getLogger(Demo.class);
    private static String sb = "/usr/local/freeswitch/sounds/en/us/callie/ivr/8000/";
    String welcome = sb + "ivr-welcome_to_freeswitch.wav";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Freeswitch password required to start inbound client.");
            new Demo(null);
        } else {
            String password = args[0];
            new Demo(password);
        }
    }

    public Demo(String freeswitchPassword) {
        try {
            if (null != freeswitchPassword) {
                final Client inboundClient = new Client();
                inboundClient.connect(new InetSocketAddress("localhost", 8021), freeswitchPassword, 10);
                inboundClient.addEventListener((ctx, event) -> logger.info("INBOUND onEslEvent: {}", event.getEventName()));
                logger.info("Inbound client started");
            } else {
                logger.info("Inbound client not started");
            }

            final SocketClient outboundServer = new SocketClient(
                    new InetSocketAddress("localhost", 8084),
                    () -> new IClientHandler() {
                        @Override
                        public void onConnect(Context context,
                                              EslEvent eslEvent) {

                            logger.info(nameMapToString(eslEvent
                                    .getMessageHeaders(), eslEvent.getEventBodyLines()));

                            String uuid = eslEvent.getEventHeaders()
                                    .get("Unique-ID");

                            logger.info(
                                    "Creating execute app for uuid {}",
                                    uuid);

                            Execute exe = new Execute(context, uuid);

                            try {

                                logger.debug("About to answer.");
                                exe.answer();
                                logger.debug("Answered!");
                                logger.debug("About to speak.");
                                exe.playback("demo/pleaseSay.wav");
                                logger.debug("Spoke!");
                                logger.debug("About to detect speech.");
                                String result = exe.playAndDetectSpeech("demo/storeNumber.wav", "unimrcp", "store_number", null);
                                logger.debug("Speech detected!");
                                logger.debug(result);
                                String speakString = parseResult(result);
                                exe.speak("flite ", "slt", "You said "+speakString);

                            } catch (ExecuteException e) {
                                logger.error(
                                        "Could not detect speech",
                                        e);
                            } finally {
                                try {
                                    exe.hangup(null);
                                    logger.debug("Execution complete!");
                                } catch (ExecuteException e) {
                                    logger.error("Could not hangup",e);
                                }
                            }

                        }

                        @Override
                        public void onEslEvent(Context ctx,
                                               EslEvent event) {
                            logger.info("OUTBOUND onEslEvent: {}",
                                    event.getEventName());

                        }
                    });
            outboundServer.start();

        } catch (Throwable t) {
            Throwables.propagate(t);
        }
    }

    public static String nameMapToString(Map<Name, String> map,
                                         List<String> lines) {
        StringBuilder sb = new StringBuilder("\nHeaders:\n");
        for (Name key : map.keySet()) {
            if(key == null)
                continue;
            sb.append(key.toString());
            sb.append("\n\t\t\t\t = \t ");
            sb.append(map.get(key));
            sb.append("\n");
        }
        if (lines != null) {
            sb.append("Body Lines:\n");
            for (String line : lines) {
                sb.append(line);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    //TODO handle parsing exceptions
    public static String parseResult(String result){
        String asrResult = null;
        try {
            logger.debug("Parsing result.");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            StringReader reader = new StringReader(result);
            InputSource in = new InputSource(reader);
            Document doc = db.parse(in);
            //Get the main element
            Element element = doc.getDocumentElement();
            //nodes are captured intent
            NodeList nodes = element.getChildNodes();
            //get the first (top) result
            Node topResult = nodes.item(0);
            logger.debug("Top result: "+topResult.getTextContent());
            //get the first child of top result, this is the literal
            asrResult = topResult.getFirstChild().getTextContent();
            logger.debug("Parsed result: "+asrResult);
            logger.debug("Result parsed!");
        } catch (Exception e) {
            logger.error("Could not parse!");
        }
        return asrResult;
    }

}
