package com.orderflow.payment.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.Topics;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/** Kafka wiring for payment-service — mirrors inventory-service pattern exactly. */
@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id:payment-service}")
  private String groupId;

  @Bean
  public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  @Bean
  public ConsumerFactory<String, CommandMessage> commandConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<CommandMessage> valueDeserializer =
        new JsonDeserializer<>(CommandMessage.class);
    valueDeserializer.addTrustedPackages("com.orderflow.common.*");
    valueDeserializer.ignoreTypeHeaders();

    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, CommandMessage> commandListenerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, CommandMessage> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(commandConsumerFactory());
    return factory;
  }

  @Bean
  public NewTopic commandsPaymentTopic() {
    return TopicBuilder.name(Topics.COMMANDS_PAYMENT).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic eventsPaymentTopic() {
    return TopicBuilder.name(Topics.EVENTS_PAYMENT).partitions(3).replicas(1).build();
  }
}
