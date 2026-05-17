package top.fengpingtech.solen.app.persistence.sqlite;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SqliteWriteCoordinator {
    private final ReentrantLock writeLock = new ReentrantLock(true);

    public <T> T withWriteLock(Supplier<T> action) {
        writeLock.lock();
        try {
            return action.get();
        } finally {
            writeLock.unlock();
        }
    }

    public void withWriteLock(Runnable action) {
        writeLock.lock();
        try {
            action.run();
        } finally {
            writeLock.unlock();
        }
    }
}
