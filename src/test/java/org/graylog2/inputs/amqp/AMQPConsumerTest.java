package org.graylog2.inputs.amqp;

import java.lang.reflect.Field;
import java.io.IOException;

import org.bson.types.ObjectId;
import org.graylog2.GraylogServerStub;
import org.graylog2.TestHelper;
import org.graylog2.inputs.amqp.AMQPConsumer;
import org.graylog2.inputs.amqp.AMQPQueueConfiguration.InputType;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;

public class AMQPConsumerTest {
	public final static String GELF_JSON = "{\"message\":\"foo\",\"host\":\"bar\",\"_lol_utf8\":\"\u00FC\"}";

	public AMQPConsumer buildConsumer() {
		AMQPQueueConfiguration amqpqueue_configuration = new AMQPQueueConfiguration(new ObjectId(),
				"exchange", "routing.key", 1, InputType.GELF, "gl2NodeId");
		return new AMQPConsumer(new GraylogServerStub(), amqpqueue_configuration);
	}
	
	@Test
	public void testAutoAckFalse() throws IOException, IllegalAccessException, NoSuchFieldException {
        AMQPConsumer consumer = buildConsumer();

		Mockery context = new Mockery();
		final Channel channel = context.mock(Channel.class);
		context.checking(new Expectations() {{
		    oneOf (channel).basicConsume(with(any(String.class)), with(equal(false)), with(any(Consumer.class)));
		}});
		
		Field field = AMQPConsumer.class.getDeclaredField("channel");
		field.setAccessible(true);
		field.set(consumer, channel);

        consumer.consume();
		context.assertIsSatisfied();
	}
	
	@Test
	public void testConsumerDoesAcknowledgeOnException() throws IOException {
        AMQPConsumer amqp = buildConsumer();

		final long deliveryTag = 3l;
		
		byte[] body = null; // invalid payload so that an Exception is thrown
		Mockery context = new Mockery();
		final Channel channel = context.mock(Channel.class);
		
		context.checking(new Expectations() {{
		    oneOf (channel).basicNack(deliveryTag, false, false);
		}});

        Consumer consumer = amqp.createConsumer(channel);
        consumer.handleDelivery("consumerTag", new Envelope(deliveryTag, true, "myexchange", "myroutingkey"), null, body);
		context.assertIsSatisfied();
	}
	
	@Test
	public void testConsumerDoesAcknowledgeOnSuccess() throws IOException {
        AMQPConsumer amqp = buildConsumer();

		final long deliveryTag = 3l;
		
		Mockery context = new Mockery();
		final Channel channel = context.mock(Channel.class);
		context.checking(new Expectations() {{
		    oneOf (channel).basicAck(deliveryTag, false);
		}});

        Consumer consumer = amqp.createConsumer(channel);
		consumer.handleDelivery("consumerTag", new Envelope(deliveryTag, true, "myexchange", "myroutingkey"), null, TestHelper.zlibCompress(GELF_JSON));
		context.assertIsSatisfied();
	}
}
