package top.fengpingtech.solen.app.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import top.fengpingtech.solen.app.config.SolenServerProperties;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.SolenServer;

class SolenServerStarterTest {
    @Test
    void stopsStartedServerOnDestroy() throws Exception {
        SolenServerStarter starter =
                new SolenServerStarter(new SolenServerProperties(), events -> {}, eventRepositoryReturningNoEvents());
        SolenServer server = mock(SolenServer.class);
        Field serverField = SolenServerStarter.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(starter, server);

        starter.destroy();

        verify(server).stop();
        assertTrue(true);
    }

    private EventRepository eventRepositoryReturningNoEvents() {
        EventRepository repository = mock(EventRepository.class);
        when(repository.getMaxId()).thenReturn(null);
        return repository;
    }
}
