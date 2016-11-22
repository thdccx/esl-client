package org.freeswitch.esl.client.internal;

import org.freeswitch.esl.client.transport.CommandResponse;
import org.freeswitch.esl.client.transport.SendMsg;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslMessage;

import java.util.concurrent.CompletableFuture;

public interface IModEslApi {

	enum EventFormat {

		PLAIN("plain"),
		XML("xml");

		private final String text;

		EventFormat(String txt) {
			this.text = txt;
		}

		@Override
		public String toString() {
			return text;
		}

	}

	enum LoggingLevel {

		CONSOLE("console"),
		DEBUG("debug"),
		INFO("info"),
		NOTICE("notice"),
		WARNING("warning"),
		ERR("err"),
		CRIT("crit"),
		ALERT("alert");

		private final String text;

		LoggingLevel(String txt) {
			this.text = txt;
		}

		@Override
		public String toString() {
			return text;
		}

	}

	boolean canSend();

	EslMessage sendApiCommand(String command, String arg);

	CompletableFuture<EslEvent> sendBackgroundApiCommand(String command, String arg);

	CompletableFuture<EslEvent> sendBackgroundApiCommand(String command, String arg, String jobId);

	CompletableFuture<EslMessage> setEventSubscriptions(EventFormat format, String events);

	CompletableFuture<EslMessage> cancelEventSubscriptions();

	CompletableFuture<EslMessage> addEventFilter(String eventHeader, String valueToFilter);

	CompletableFuture<EslMessage> deleteEventFilter(String eventHeader, String valueToFilter);

	CommandResponse sendMessage(SendMsg sendMsg);

	CompletableFuture<EslMessage> setLoggingLevel(LoggingLevel level);

	CompletableFuture<EslMessage> cancelLogging();
}
