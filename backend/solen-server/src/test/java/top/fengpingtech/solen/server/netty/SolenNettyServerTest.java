package top.fengpingtech.solen.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Test;
import top.fengpingtech.solen.server.config.ServerProperties;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class SolenNettyServerTest {
    @Test
    public void closesChannelBeforeWaitingForShutdown() throws Exception {
        SolenNettyServer server = new SolenNettyServer(new ServerProperties());
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        ChannelFuture closeFuture = channelFuture(closeCalled, null);
        Channel channel = channel(closeCalled, closeFuture);
        ChannelFuture serverFuture = channelFuture(closeCalled, channel);

        setField(server, "future", serverFuture);
        setField(server, "executors", Collections.emptyList());

        server.stop();

        assertTrue("expected stop() to close the bound channel", closeCalled.get());
    }

    private static Channel channel(AtomicBoolean closeCalled, ChannelFuture closeFuture) {
        return (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(),
                new Class<?>[] {Channel.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("close".equals(method.getName())) {
                            closeCalled.set(true);
                            return closeFuture;
                        }
                        if ("closeFuture".equals(method.getName())) {
                            return closeFuture;
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        }
                        return null;
                    }
                }
        );
    }

    private static ChannelFuture channelFuture(AtomicBoolean closeCalled, Channel channel) {
        return (ChannelFuture) Proxy.newProxyInstance(
                ChannelFuture.class.getClassLoader(),
                new Class<?>[] {ChannelFuture.class},
                new InvocationHandler() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                        if ("channel".equals(method.getName())) {
                            return channel;
                        }
                        if ("addListener".equals(method.getName())) {
                            GenericFutureListener<ChannelFuture> listener = (GenericFutureListener<ChannelFuture>) args[0];
                            listener.operationComplete((ChannelFuture) proxy);
                            return proxy;
                        }
                        if ("sync".equals(method.getName())) {
                            return proxy;
                        }
                        if ("isDone".equals(method.getName())) {
                            return true;
                        }
                        if ("isSuccess".equals(method.getName())) {
                            return true;
                        }
                        if ("cause".equals(method.getName())) {
                            return null;
                        }
                        if ("isCancelled".equals(method.getName())) {
                            return false;
                        }
                        if ("getNow".equals(method.getName())) {
                            return null;
                        }
                        if ("await".equals(method.getName()) || "awaitUninterruptibly".equals(method.getName())) {
                            return proxy;
                        }
                        if ("cancel".equals(method.getName())) {
                            return false;
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        }
                        if (method.getReturnType() == boolean.class) {
                            return false;
                        }
                        if (method.getReturnType() == int.class) {
                            return 0;
                        }
                        if (method.getReturnType().isInstance(proxy)) {
                            return proxy;
                        }
                        return null;
                    }
                }
        );
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
