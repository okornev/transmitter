/*******************************************************************************
 *
 *   Copyright 2015 Walmart, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *******************************************************************************/
package com.oneops.cms.transmitter;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.oneops.util.MessageData;

public class JMSConsumer implements ExceptionListener {

    private ActiveMQConnectionFactory connectionFactory;
    private String destinationName;
    private String destinationType;

    private MessageConsumer consumer;
    private Session session;
    private Connection connection;

    private final AtomicInteger counter = new AtomicInteger(0);

    private LinkedList<MessageData> messages;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    public void init() {
        new Thread(() -> startConsumer()).start();
    }

    private void startConsumer() {
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination;
            if ("topic".equalsIgnoreCase(destinationType)) {
                destination = session.createTopic(destinationName);
            } else {
                destination = session.createQueue(destinationName);
            }

            consumer = session.createConsumer(destination);
            isStarted.compareAndSet(false, true);
            while (true) {
                Message message = consumer.receive();

                if (message instanceof TextMessage) {
                    TextMessage textMessage = (TextMessage) message;
                    String text = textMessage.getText();
                    if (isRecording.get()) {
                        addData(message, text);
                    }
                    counter.incrementAndGet();
                }
            }

        } catch (Exception e) {
            //e.printStackTrace();
        } finally {
            terminate();
        }
    }

    private void addData(Message message, String text) throws JMSException {
        MessageData data = new MessageData();
        data.setPayload(text);
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = message.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = message.getStringProperty(name);
            headers.put(name, value);
        }
        data.setHeaders(headers);
        messages.add(data);
    }

    public void startRecording() {
        messages = new LinkedList<>();
        isRecording.getAndSet(true);
    }

    public void stopRecording() {
        messages = null;
        isRecording.getAndSet(false);
    }

    public boolean isStarted() {
        return isStarted.get();
    }

    public void terminate() {
        try {
            consumer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {

        }
    }

    public void resetCounter() {
        counter.getAndSet(0);
    }

    @Override
    public void onException(JMSException exception) {
        exception.printStackTrace();
    }

    public int getCounter() {
        return counter.get();
    }

    public void setConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }

    public LinkedList<MessageData> getMessages() {
        return messages;
    }

}
