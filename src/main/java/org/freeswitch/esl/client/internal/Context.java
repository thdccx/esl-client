package org.freeswitch.esl.client.internal;

import io.netty.channel.Channel;
import org.freeswitch.esl.client.transport.CommandResponse;
import org.freeswitch.esl.client.transport.SendMsg;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static java.util.Objects.isNull;
import static org.freeswitch.esl.client.internal.IModEslApi.EventFormat.*;

public class Context implements IModEslApi {

	private final AbstractEslClientHandler handler;
	private final Channel channel;

	public Context(Channel channel, AbstractEslClientHandler clientHandler) {
		this.handler = clientHandler;
		this.channel = channel;
	}

	@Override
	public boolean canSend() {
		return channel != null && channel.isActive();
	}

	@Override
	public boolean isConnectionAlive(Integer pingTimeoutSecond) {
		try {
			handler.sendApiSingleLineCommand(channel, "api version short").get(pingTimeoutSecond, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException | TimeoutException e) {
			return false;
		}
		return true;
	}

	/**
	 * Sends a mod_event_socket command to FreeSWITCH server and blocks, waiting for an immediate response from the
	 * server.
	 * <p/>
	 * The outcome of the command from the server is returned in an {@link org.freeswitch.esl.client.transport.message.EslMessage} object.
	 *
	 * @param command a mod_event_socket command to send
	 * @return an {@link org.freeswitch.esl.client.transport.message.EslMessage} containing command results
	 */
	public EslMessage sendCommand(String command) {

		checkArgument(!isNullOrEmpty(command), "command cannot be null or empty");

		try {

			return getUnchecked(handler.sendApiSingleLineCommand(channel, command.toLowerCase().trim()));

		} catch (Throwable t) {
			throw propagate(t);
		}
	}

	/**
	 * Sends a FreeSWITCH API command to the server and blocks, waiting for an immediate response from the
	 * server.
	 * <p/>
	 * The outcome of the command from the server is returned in an {@link org.freeswitch.esl.client.transport.message.EslMessage} object.
	 *
	 * @param command API command to send
	 * @param arg     command arguments
	 * @return an {@link org.freeswitch.esl.client.transport.message.EslMessage} containing command results
	 */
	@Override
	public EslMessage sendApiCommand(String command, String arg) {

		checkArgument(!isNullOrEmpty(command), "command cannot be null or empty");

		try {

			final StringBuilder sb = new StringBuilder();
			sb.append("api ").append(command);
			if (!isNullOrEmpty(arg)) {
				sb.append(' ').append(arg);
			}

			return getUnchecked(handler.sendApiSingleLineCommand(channel, sb.toString()));

		} catch (Throwable t) {
			throw propagate(t);
		}
	}

	/**
	 * Submit a FreeSWITCH API command to the server to be executed in background mode. A synchronous
	 * response from the server provides a UUID to identify the job execution results. When the server
	 * has completed the job execution it fires a BACKGROUND_JOB Event with the execution results.<p/>
	 * Note that this Client must be subscribed in the normal way to BACKGROUND_JOB Events, in order to
	 * receive this event.
	 *
	 * @param command API command to send
	 * @param arg     command arguments
	 * @return String Job-UUID that the server will tag result event with.
	 */
	@Override
	public CompletableFuture<EslEvent> sendBackgroundApiCommand(String command, String arg) {

		checkArgument(!isNullOrEmpty(command), "command cannot be null or empty");

		final StringBuilder sb = new StringBuilder();
		sb.append("bgapi ").append(command);
		if (!isNullOrEmpty(arg)) {
			sb.append(' ').append(arg);
		}

		return handler.sendBackgroundApiCommand(channel, sb.toString());
	}

	/**
	 * Submit a FreeSWITCH API command with predefined Job-UUID to the server to be executed in background mode. When the server
	 * has completed the job execution it fires a BACKGROUND_JOB Event with the execution results.
	 * Note that this Client must be subscribed in the normal way to BACKGROUND_JOB Events, in order to
	 * receive this event.
	 * @param command API command to send
	 * @param arg command arguments
	 * @param jobId Job-UUID that the server will tag result event with.
	 * @return promis with EslEvent containing results
	 */
	@Override
	public CompletableFuture<EslEvent> sendBackgroundApiCommand(String command, String arg, String jobId) {
		checkArgument(!isNullOrEmpty(command), "command cannot be null or empty");
		checkArgument(!isNull(jobId), "command cannot be null");

		final StringBuilder sb = new StringBuilder();
		sb.append("bgapi ").append(command);
		if (!isNullOrEmpty(arg)) {
			sb.append(' ').append(arg);
		}

		return handler.sendBackgroundApiCommand(channel, sb.toString(), jobId);
	}

	/**
	 * Set the current event subscription for this connection to the server.  Examples of the events
	 * argument are:
	 * <pre>
	 *   ALL
	 *   CHANNEL_CREATE CHANNEL_DESTROY HEARTBEAT
	 *   CUSTOM conference::maintenance
	 *   CHANNEL_CREATE CHANNEL_DESTROY CUSTOM conference::maintenance sofia::register sofia::expire
	 * </pre>
	 * Subsequent calls to this method replaces any previous subscriptions that were set.
	 * </p>
	 * Note: current implementation can only process 'plain' events.
	 *
	 * @param format can be { plain | xml }
	 * @param events { all | space separated list of events }
	 * @return a {@link CompletableFuture<EslMessage>} with the server's response.
	 */
	@Override
	public CompletableFuture<EslMessage> setEventSubscriptions(EventFormat format, String events) {

		// temporary hack
		checkState(format.equals(PLAIN), "Only 'plain' event format is supported at present");

		final StringBuilder sb = new StringBuilder();
		sb.append("event ").append(format.toString());
		if (!isNullOrEmpty(events)) {
			sb.append(' ').append(events);
		}

		return handler.sendApiSingleLineCommand(channel, sb.toString());

	}

	/**
	 * Cancel any existing event subscription.
	 *
	 * @return a {@link CompletableFuture<EslMessage>} with the server's response.
	 */
	@Override
	public CompletableFuture<EslMessage> cancelEventSubscriptions() {
		return handler.sendApiSingleLineCommand(channel, "noevents");
	}

	/**
	 * Add an event filter to the current set of event filters on this connection. Any of the event headers
	 * can be used as a filter.
	 * </p>
	 * Note that event filters follow 'filter-in' semantics. That is, when a filter is applied
	 * only the filtered values will be received. Multiple filters can be added to the current
	 * connection.
	 * </p>
	 * Example filters:
	 * <pre>
	 *    eventHeader        valueToFilter
	 *    ----------------------------------
	 *    Event-Name         CHANNEL_EXECUTE
	 *    Channel-State      CS_NEW
	 * </pre>
	 *
	 * @param eventHeader   to filter on
	 * @param valueToFilter the value to match
	 * @return a {@link CompletableFuture<EslMessage>} with the server's response.
	 */
	@Override
	public CompletableFuture<EslMessage> addEventFilter(String eventHeader, String valueToFilter) {

		checkArgument(!isNullOrEmpty(eventHeader), "eventHeader cannot be null or empty");

		final StringBuilder sb = new StringBuilder();
		sb.append("filter ").append(eventHeader);
		if (!isNullOrEmpty(valueToFilter)) {
			sb.append(' ').append(valueToFilter);
		}

		return handler.sendApiSingleLineCommand(channel, sb.toString());
	}

	/**
	 * Delete an event filter from the current set of event filters on this connection.  See
	 *
	 * @param eventHeader   to remove
	 * @param valueToFilter to remove
	 * @return a {@link CommandResponse} with the server's response.
	 */
	@Override
	public CompletableFuture<EslMessage> deleteEventFilter(String eventHeader, String valueToFilter) {

		checkArgument(!isNullOrEmpty(eventHeader), "eventHeader cannot be null or empty");

		final StringBuilder sb = new StringBuilder();
		sb.append("filter delete ").append(eventHeader);
		if (!isNullOrEmpty(valueToFilter)) {
			sb.append(' ').append(valueToFilter);
		}
		return handler.sendApiSingleLineCommand(channel, sb.toString());
	}

	/**
	 * Send a {@link SendMsg} command to FreeSWITCH.  This client requires that the {@link SendMsg}
	 * has a call UUID parameter.
	 *
	 * @param sendMsg a {@link SendMsg} with call UUID
	 * @return a {@link CommandResponse} with the server's response.
	 */
	@Override
	public CommandResponse sendMessage(SendMsg sendMsg) {

		checkNotNull(sendMsg, "sendMsg cannot be null");

		try {
			final EslMessage response = getUnchecked(handler.sendApiMultiLineCommand(channel, sendMsg.getMsgLines()));
			return new CommandResponse(sendMsg.toString(), response);
		} catch (Throwable t) {
			throw propagate(t);
		}

	}

	/**
	 * Enable log output.
	 *
	 * @param level using the same values as in console.conf
	 * @return a {@link CompletableFuture<EslMessage>} with the server's response.
	 */
	@Override
	public CompletableFuture<EslMessage> setLoggingLevel(LoggingLevel level) {
		final StringBuilder sb = new StringBuilder();
		sb.append("log ").append(level.toString());

		return handler.sendApiSingleLineCommand(channel, sb.toString());
	}

	/**
	 * Disable any logging previously enabled with setLogLevel().
	 *
	 * @return a {@link CommandResponse} with the server's response.
	 */
	@Override
	public CompletableFuture<EslMessage> cancelLogging() {
		return handler.sendApiSingleLineCommand(channel, "nolog");
	}

  public void closeChannel() {
      try {
          if(channel != null && channel.isOpen())
              channel.close();
      } catch (Throwable t) {
          throw propagate(t);
      }
  }
}
