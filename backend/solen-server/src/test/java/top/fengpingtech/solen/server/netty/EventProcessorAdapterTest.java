package top.fengpingtech.solen.server.netty;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import org.junit.Test;
import top.fengpingtech.solen.server.EventProcessor;
import top.fengpingtech.solen.server.IdGenerator;
import top.fengpingtech.solen.server.model.Event;
import top.fengpingtech.solen.server.model.EventType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EventProcessorAdapterTest {
    @Test
    public void exceptionCaughtPublishesDisconnectEventWithTimestamp() {
        List<Event> processedEvents = new ArrayList<>();
        EventProcessor eventProcessor = processedEvents::addAll;
        IdGenerator idGenerator = () -> 1001L;
        EmbeddedChannel channel = new EmbeddedChannel(new EventProcessorAdapter(eventProcessor, idGenerator));
        channel.attr(AttributeKey.valueOf("DeviceId")).set("device-10086");

        channel.pipeline().fireExceptionCaught(new RuntimeException("disconnect"));

        assertEquals(1, processedEvents.size());
        Event event = processedEvents.get(0);
        assertEquals(EventType.DISCONNECT, event.getType());
        assertEquals(Long.valueOf(1001L), event.getEventId());
        assertEquals("device-10086", event.getDeviceId());
        assertEquals(channel.id().asLongText(), event.getConnectionId());
        assertNotNull(event.getTime());
    }
}

