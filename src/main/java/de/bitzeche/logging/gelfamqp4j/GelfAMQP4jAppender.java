/**
 * Copyright (C) 2013 Bitzeche GmbH <info@bitzeche.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.bitzeche.logging.gelfamqp4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.graylog2.GelfMessage;
import org.graylog2.GelfMessageFactory;
import org.graylog2.GelfMessageProvider;
import org.json.simple.JSONValue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * This appender sends GELF messages over AMQP.
 * 
 * @author Patrice Brend'amour
 */
public class GelfAMQP4jAppender extends AppenderSkeleton implements
		GelfMessageProvider {

	private String facility;
	private boolean extractStacktrace;
	private boolean addExtendedInformation;
	private boolean includeLocation = true;
	private Map<String, String> fields;
	private String originHost;
	private String amqpUserName;
	private String amqpPassword;
	private String amqpVirtualHost;
	private String amqpHostName;
	private String amqpExchangeName;
	private String amqpRoutingKey;
	private int amqpPortNumber;

	private ConnectionFactory factory;
	private Connection connection;
	private Channel channel;

	public GelfAMQP4jAppender() {
		super();
	}

	@Override
	public void activateOptions() {
		if (StringUtils.isBlank(amqpUserName)
				|| StringUtils.isBlank(amqpPassword)
				|| StringUtils.isBlank(amqpVirtualHost)
				|| StringUtils.isBlank(amqpHostName) || amqpPortNumber <= 0
				|| StringUtils.isBlank(amqpExchangeName)
				|| StringUtils.isBlank(amqpRoutingKey)) {
			errorHandler.error("AMQP is not configured correctly!", null,
					ErrorCode.WRITE_FAILURE);
		} else {
			factory = new ConnectionFactory();
			factory.setUsername(amqpUserName);
			factory.setPassword(amqpPassword);
			factory.setVirtualHost(amqpVirtualHost);
			factory.setHost(amqpHostName);
			factory.setPort(amqpPortNumber);
		}
	}

	@Override
	protected void append(LoggingEvent event) {
		GelfMessage gelfMessage = GelfMessageFactory.makeMessage(event, this);
		if (factory == null || !sendMessage(gelfMessage)) {
			errorHandler.error("Could not send GELF message");
		}
	}

	@Override
	public void close() {
		if (connection != null) {
			try {
				channel.waitForConfirms();
				channel.close();
				connection.close();
			} catch (Exception e) {
				errorHandler.error("Socket exception", e,
						ErrorCode.WRITE_FAILURE);
			}
		}
	}

	@Override
	public boolean requiresLayout() {
		return false;
	}

	private boolean sendMessage(GelfMessage message) {
		try {
			openGelfConnection();
			byte[] messageBodyBytes = message.toBuffer().array();
			channel.basicPublish(amqpExchangeName, amqpRoutingKey, null,
					messageBodyBytes);
			return true;
		} catch (IOException e) {
			errorHandler.error("Socket exception", e, ErrorCode.WRITE_FAILURE);
		}
		return false;
	}

	private void openGelfConnection() throws IOException {
		if (connection == null || !connection.isOpen()) {
			connection = factory.newConnection();
			channel = connection.createChannel();
			channel.exchangeDeclare(amqpExchangeName, "topic", true);
			channel.confirmSelect();
		}
	}

	@SuppressWarnings("unchecked")
	public void setAdditionalFields(String additionalFields) {
		fields = (Map<String, String>) JSONValue.parse(additionalFields
				.replaceAll("'", "\""));
	}

	@Override
	public Map<String, String> getFields() {
		if (fields == null) {
			fields = new HashMap<String, String>();
		}
		return Collections.unmodifiableMap(fields);
	}

	public String getOriginHost() {
		if (originHost == null) {
			originHost = getLocalHostName();
		}
		return originHost;
	}

	private String getLocalHostName() {
		String hostName = null;
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			errorHandler.error("Unknown local hostname", e,
					ErrorCode.GENERIC_FAILURE);
		}

		return hostName;
	}

	public String getFacility() {
		return facility;
	}

	public void setFacility(String facility) {
		this.facility = facility;
	}

	public boolean isExtractStacktrace() {
		return extractStacktrace;
	}

	public void setExtractStacktrace(boolean extractStacktrace) {
		this.extractStacktrace = extractStacktrace;
	}

	public boolean isAddExtendedInformation() {
		return addExtendedInformation;
	}

	public void setAddExtendedInformation(boolean addExtendedInformation) {
		this.addExtendedInformation = addExtendedInformation;
	}

	public boolean isIncludeLocation() {
		return includeLocation;
	}

	public void setIncludeLocation(boolean includeLocation) {
		this.includeLocation = includeLocation;
	}

	public void setAmqpUserName(String amqpUserName) {
		this.amqpUserName = amqpUserName;
	}

	public void setAmqpPassword(String amqpPassword) {
		this.amqpPassword = amqpPassword;
	}

	public void setAmqpVirtualHost(String amqpVirtualHost) {
		this.amqpVirtualHost = amqpVirtualHost;
	}

	public void setAmqpHostName(String amqpHostName) {
		this.amqpHostName = amqpHostName;
	}

	public void setAmqpExchangeName(String amqpExchangeName) {
		this.amqpExchangeName = amqpExchangeName;
	}

	public void setAmqpRoutingKey(String amqpRoutingKey) {
		this.amqpRoutingKey = amqpRoutingKey;
	}

	public void setAmqpPortNumber(int amqpPortNumber) {
		this.amqpPortNumber = amqpPortNumber;
	}

	public void setOriginHost(String originHost) {
		this.originHost = originHost;
	}

}
