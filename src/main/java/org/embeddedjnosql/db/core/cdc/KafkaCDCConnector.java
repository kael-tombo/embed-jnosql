package org.embeddedjnosql.db.core.cdc;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Properties;

public class KafkaCDCConnector implements AutoCloseable {

    private final CDCProcessor processor;
    private final String bootstrapServers;
    private final String topic;
    private final Properties props;
    private final ExecutorService producerExecutor;
    private final BlockingQueue<CDCEvent> queue;
    private final AtomicBoolean running;
    private volatile Object kafkaProducer;

    public KafkaCDCConnector(CDCProcessor processor, String bootstrapServers, String topic) {
        this.processor = processor;
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.queue = new LinkedBlockingQueue<>(10000);
        this.running = new AtomicBoolean(false);
        this.producerExecutor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "CDC-KafkaProducer");
            t.setDaemon(true);
            return t;
        });

        this.props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
    }

    public void start() {
        if (running.getAndSet(true)) return;
        
        try {
            var producerClass = Class.forName("org.apache.kafka.clients.producer.KafkaProducer");
            var propsArg = new Properties(props);
            kafkaProducer = producerClass.getConstructor(Properties.class).newInstance(propsArg);
        } catch (Exception e) {
            System.err.println("Kafka not available: " + e.getMessage());
            System.err.println("CDC events will be logged but not sent to Kafka");
            return;
        }

        producerExecutor.submit(this::sendLoop);
        processor.subscribe(this::onEvent);
    }

    private void onEvent(CDCEvent event) {
        queue.offer(event);
    }

    private void sendLoop() {
        while (running.get()) {
            try {
                CDCEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event == null || kafkaProducer == null) continue;

                var producerClass = kafkaProducer.getClass();
                var record = createProducerRecord(topic, event.collection(), event.toJson());
                
                var sendMethod = producerClass.getMethod("send", Class.forName("org.apache.kafka.clients.producer.ProducerRecord"));
                sendMethod.invoke(kafkaProducer, record);
                
                var flushMethod = producerClass.getMethod("flush");
                flushMethod.invoke(kafkaProducer);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Kafka send error: " + e.getMessage());
            }
        }
    }

    private Object createProducerRecord(String topic, String key, String value) throws Exception {
        var recordClass = Class.forName("org.apache.kafka.clients.producer.ProducerRecord");
        return recordClass.getConstructor(String.class, String.class, String.class)
            .newInstance(topic, key, value);
    }

    public int queueSize() {
        return queue.size();
    }

    @Override
    public void close() {
        running.set(false);
        
        if (kafkaProducer != null) {
            try {
                var flushMethod = kafkaProducer.getClass().getMethod("flush");
                flushMethod.invoke(kafkaProducer);
                
                var closeMethod = kafkaProducer.getClass().getMethod("close");
                closeMethod.invoke(kafkaProducer);
            } catch (Exception ignored) {}
        }

        producerExecutor.shutdown();
        try {
            producerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}