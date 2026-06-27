package com.orderflow.order.messaging;

import com.orderflow.common.messaging.EventMessage;
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

/**
 * Kafka wiring for the orchestrator.
 *
 * <ul>
 *   <li>Producer is String→String: the outbox already stored a JSON string, so we ship it as-is
 *       (no double-encoding). The relay uses this template.
 *   <li>Consumer deserializes inventory result events to the single {@link EventMessage} type.
 * </ul>
 */
@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id:order-service}")
  private String groupId;

  @Bean
  public ProducerFactory<String, String> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, String> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  @Bean
  public ConsumerFactory<String, EventMessage> eventConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    JsonDeserializer<EventMessage> valueDeserializer = new JsonDeserializer<>(EventMessage.class);
    valueDeserializer.addTrustedPackages("com.orderflow.common.*");
    valueDeserializer.ignoreTypeHeaders();

    return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, EventMessage> eventListenerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, EventMessage> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(eventConsumerFactory());
    return factory;
  }

  @Bean
  public NewTopic commandsInventoryTopic() {
    return TopicBuilder.name(Topics.COMMANDS_INVENTORY).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic eventsInventoryTopic() {
    return TopicBuilder.name(Topics.EVENTS_INVENTORY).partitions(3).replicas(1).build();
  }
}
