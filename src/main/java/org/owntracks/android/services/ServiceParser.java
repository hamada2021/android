package org.owntracks.android.services;

import android.content.Intent;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.owntracks.android.App;
import org.owntracks.android.messages.MessageBase;
import org.owntracks.android.messages.MessageCard;
import org.owntracks.android.messages.MessageCmd;
import org.owntracks.android.messages.MessageEncrypted;
import org.owntracks.android.messages.MessageLocation;
import org.owntracks.android.messages.MessageTransition;
import org.owntracks.android.messages.MessageUnknown;
import org.owntracks.android.model.FusedContact;
import org.owntracks.android.support.EncryptionProvider;
import org.owntracks.android.support.Events;
import org.owntracks.android.support.GeocodingProvider;
import org.owntracks.android.support.IncomingMessageProcessor;
import org.owntracks.android.support.Preferences;
import org.owntracks.android.support.receiver.Parser;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceParser implements ProxyableService, IncomingMessageProcessor {
    public static final String TAG = "ServiceParser";
    ObjectMapper mapper;
    ServiceProxy context;
    private ThreadPoolExecutor pool;

    @Override
    public void processMessage(MessageBase message) {
        Log.v(TAG, "processMessage MessageBase (" + message.getTopic()+")");
    }

    public void processMessage(MessageUnknown message) {
        Log.v(TAG, "processMessage MessageUnknown (" + message.getTopic()+")");
    }


    @Override
    public void processMessage(MessageLocation message) {
        Log.v(TAG, "processMessage MessageLocation (" + message.getTopic()+")");

        GeocodingProvider.resolve(message);
        FusedContact c = App.getFusedContact(message.getTopic());

        if (c == null) {
            c = new FusedContact(message.getTopic());
            c.setMessageLocation(message);
            App.addFusedContact(c);
        } else {
            c.setMessageLocation(message);
        }
    }

    @Override
    public void processMessage(MessageCard message) {
        Log.v(TAG, "processMessage MessageCard (" + message.getTopic() + ")");
        FusedContact c = App.getFusedContact(message.getTopic());

        if (c == null) {
            c = new FusedContact(message.getTopic());
            c.setMessageCard(message);
            App.addFusedContact(c);
        } else {
            c.setMessageCard(message);
        }
    }

    @Override
    public void processMessage(MessageCmd message) {
        Log.v(TAG, "processMessage MessageCmd (" + message.getTopic() + ")");
    }

    @Override
    public void processMessage(MessageTransition message) {
        Log.v(TAG, "processMessage MessageTransition (" + message.getTopic() + ")");
        ServiceProxy.getServiceNotification().processMessage(message);
    }

    public void onCreate(ServiceProxy c) {
        this.mapper = new ObjectMapper();
        this.context = c;
        pool= new ThreadPoolExecutor(2,2,1,  TimeUnit.MINUTES,new LinkedBlockingQueue<Runnable>());

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onStartCommand(Intent intent, int flags, int startId) {
        return;
    }

    @Override
    public void onEvent(Events.Dummy event) {

    }

    private String getBaseTopic(MessageBase message, String topic){

        if (message.getBaseTopicSuffix() != null && topic.endsWith(message.getBaseTopicSuffix())) {
            return topic.substring(0, (topic.length() - message.getBaseTopicSuffix().length()));
        } else {
            return topic;
        }
    }

    public void fromJSON(String topic, MqttMessage message) {
        try {
            MessageBase m = Parser.deserializeSync(message.getPayload());
            m.setTopic(getBaseTopic(m, topic));
            m.setRetained(message.isRetained());
            m.setQos(message.getQos());
            m.setIncomingProcessor(this);
            pool.execute(m);

        } catch (Exception e) {

            Log.e(TAG, "JSON parser exception for message: " + new String(message.getPayload()));
            Log.e(TAG, e.getMessage() +" " + e.getCause());

            e.printStackTrace();
        }
    }


        public void parseIncomingBrokerMessage(String topic, MqttMessage message) throws Exception {
        fromJSON(topic, message);
    }
}