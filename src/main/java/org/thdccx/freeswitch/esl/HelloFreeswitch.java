package org.thdccx.freeswitch.esl;

import com.google.common.base.Throwables;
import org.freeswitch.esl.client.dptools.Execute;
import org.freeswitch.esl.client.dptools.ExecuteException;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.internal.Context;
import org.freeswitch.esl.client.outbound.IClientHandler;
import org.freeswitch.esl.client.outbound.SocketClient;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class HelloFreeswitch {
    private static Logger logger = LoggerFactory.getLogger(HelloFreeswitch.class);
    private static String sb = "/usr/local/freeswitch/sounds/en/us/callie/ivr/8000/";
    String welcome = sb + "ivr-welcome_to_freeswitch.wav";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Freeswitch password required to start inbound client.");
            new HelloFreeswitch(null);
        } else {
            String password = args[0];
            new HelloFreeswitch(password);
        }
    }

    public HelloFreeswitch(String freeswitchPassword) {
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
                        public void onConnect(Context context, EslEvent eslEvent) {

                            String uuid = eslEvent.getEventHeaders().get("Unique-ID");
                            logger.info("Creating execute app for uuid {}", uuid);
                            Execute exe = new Execute(context, uuid);

                            try {

                                logger.debug("About to answer.");
                                exe.answer();
                                logger.debug("Answered!");
                                logger.debug("About to playback.");
                                exe.playback(welcome);
                                logger.debug("Played back!");
                                logger.debug("About to speak.");
                                exe.speak("flite ", "slt", "Welcome to freeswitch, the future of telephony.");
                                logger.debug("Spoke!");

                            } catch (ExecuteException e) {
                                logger.error(
                                        "Could not execute program",
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
                        public void onEslEvent(Context ctx, EslEvent event) {
                            logger.info("OUTBOUND onEslEvent: {}", event.getEventName());

                        }
                    });
            outboundServer.start();

        } catch (Throwable t) {
            Throwables.propagate(t);
        }
    }

}
