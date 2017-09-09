package com.example.mehmetg.samplesatoriapp;

import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.accessibility.AccessibilityEventSource;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.satori.rtm.Ack;
import com.satori.rtm.RtmClient;
import com.satori.rtm.RtmClientAdapter;
import com.satori.rtm.RtmClientBuilder;
import com.satori.rtm.SubscriptionAdapter;
import com.satori.rtm.SubscriptionMode;
import com.satori.rtm.model.AnyJson;
import com.satori.rtm.model.CommonError;
import com.satori.rtm.model.Pdu;
import com.satori.rtm.model.PduException;
import com.satori.rtm.model.PduRaw;
import com.satori.rtm.model.PublishReply;
import com.satori.rtm.model.SubscribeReply;
import com.satori.rtm.model.SubscribeRequest;
import com.satori.rtm.model.SubscriptionData;
import com.satori.rtm.model.SubscriptionError;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity {

    private final static String endpoint = "wss://hicqa49x.api.satori.com";
    private final static String appKey = "7Ae3B8F2a619a6A1EEBec522ACdD208F";
    private final static String channelName = "esp";
    private Switch blinkOn;
    private Switch ledOn;
    private SeekBar period;
    private TextView endPointTextView;
    private TextView appKeyTextView;
    private TextView channelTextView;
    private TextView messagesTextView;
    private Button clearButton;
    private Button timeButton;
    private RtmClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        client = new RtmClientBuilder(endpoint, appKey)
                .setListener(new RtmClientAdapter() {
                    @Override
                    public void onError(RtmClient client, Exception ex) {
                        System.out.println("Error occurred: " + ex.getMessage());
                    }

                    @Override
                    public void onConnectingError(RtmClient client, Exception ex) {
                        System.out.println("Error occurred: " + ex.getMessage());
                    }

                    @Override
                    public void onEnterConnected(RtmClient client) {
                        System.out.println("Connected to Satori RTM!");
                    }
                })
                .build();
        SubscriptionAdapter listener = new SubscriptionAdapter() {
            @Override
            public void onEnterSubscribed(SubscribeRequest request, SubscribeReply reply) {
                System.out.println("Subscribed to: " + reply.getSubscriptionId());
            }

            @Override
            public void onLeaveSubscribed(SubscribeRequest request, SubscribeReply reply) {
                System.out.println("Unsubscribed from: " + reply.getSubscriptionId());
            }

            @Override
            public void onSubscriptionError(SubscriptionError error) {
                String txt = String.format(
                        "Subscription failed. RTM sent the error %s: %s", error.getError(), error.getReason());
                System.out.println(txt);
            }

            @Override
            public void onSubscriptionData(SubscriptionData data) {
                for (AnyJson json : data.getMessages()) {
                    try {
                        String msg = String.format("%s%n", json.toString());
                        messagesTextView.append(msg);
                    } catch (Exception ex) {
                        System.out.println("Failed to deserialize the incoming message: " + ex);
                    }
                }
            }
        };
        client.createSubscription(channelName, SubscriptionMode.SIMPLE, listener);
        client.start();
        publishMessage(client, "started", null);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        blinkOn = (Switch) findViewById(R.id.blinkOn);
        ledOn = (Switch) findViewById(R.id.ledOn);
        period = (SeekBar) findViewById(R.id.period);
        endPointTextView = (TextView) findViewById(R.id.endPoint);
        appKeyTextView = (TextView) findViewById(R.id.appKey);
        channelTextView = (TextView) findViewById(R.id.channel);
        messagesTextView = (TextView) findViewById(R.id.messages);
        clearButton = (Button) findViewById(R.id.clear);
        timeButton = (Button) findViewById(R.id.time);

        messagesTextView.setText("");
        appKeyTextView.setText(appKey);
        endPointTextView.setText(endpoint);
        channelTextView.setText(channelName);

        blinkOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                publishMessage(client, b ? "blink_on" : "blink_off", blinkOn);
            }
        });
        ledOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                publishMessage(client, b ? "led_on" : "led_off", ledOn);
            }
        });
        period.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int p = seekBar.getProgress();
                publishMessage(client, p * 20, period);
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                messagesTextView.setText("");
            }
        });
        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               publishMessage(client, "time", timeButton);
            }
        });
        messagesTextView.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onPause() {
        client.stop();
        super.onPause();
    }

    @Override
    protected void onResume() {
        client.start();
        super.onResume();
    }

    private static ListenableFuture publishMessage(final RtmClient client, Object message, final View v) {
        ListenableFuture<Pdu<PublishReply>> reply = client.publish(channelName, message, Ack.YES);
        Futures.addCallback(reply, new FutureCallback<Pdu<PublishReply>>() {
            public void onSuccess(Pdu<PublishReply> pdu) {
                System.out.println("Publish confirmed");
                if (v != null) {
                    v.setEnabled(true);
                }
            }

            public void onFailure(Throwable caught) {
                try {
                    throw caught;
                } catch (PduException e) {
                    PduRaw pdu = e.getPdu();
                    CommonError error = pdu.convertBodyTo(CommonError.class).getBody();
                    System.out.println(String.format("Failed to publish. RTM replied with the error %s: %s",
                            error.getError(), error.getReason()));
                } catch (Throwable e) {
                    System.out.println("Failed to publish: " + e);
                } finally {
                    if (v != null) {
                        v.setEnabled(true);
                    }
                }
            }
        });
        return reply;
    }

    @Override
    protected void onDestroy() {
        client.shutdown();
        super.onDestroy();
    }
}
