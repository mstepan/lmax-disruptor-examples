package org.max.lmax.disruptor;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class LmaxDisruptorMain {

    static class CreateUserEvent {
        String username;

        CreateUserEvent(String username) {
            this.username = username;
        }
    }

    static class CustomThreadFactory implements ThreadFactory {

        private final AtomicInteger threadsCounter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable action) {
            Thread th = new Thread(action);
            th.setName("lmax-consumer-" + threadsCounter.incrementAndGet());
            return th;
        }
    }

    /**
     * For details read:
     * <a href="https://itnext.io/understanding-the-lmax-disruptor-caaaa2721496">Understanding the LMAX Disruptor</a>
     * <a href="https://lmax-exchange.github.io/disruptor/user-guide/index.html">LMAX Disruptor User Guide</a>
     */
    public static void main(String[] args) throws Exception {

        final Disruptor<CreateUserEvent> disruptor =
            new Disruptor<>(
                () -> new CreateUserEvent(null), // Factory for events
                8, // Ring buffer size
                new CustomThreadFactory(),
                ProducerType.MULTI, // we have 2 threads as producers, so MULTI here, otherwise SINGLE
                new BusySpinWaitStrategy() // burn CPU cycles in favour of latency strategy
            );

        @SuppressWarnings("unchecked") final EventHandler<CreateUserEvent>[] consumers = new EventHandler[3];

        for (int i = 0; i < consumers.length; ++i) {

            final int curConsumerIdx = i;

            // sharding for consumers
            consumers[i] = (event, sequence, endOfBatch) -> {
                if ((sequence % consumers.length) == curConsumerIdx) {
                    TimeUnit.MILLISECONDS.sleep(150L);
                    System.out.printf("%s: ==> %s\n", Thread.currentThread().getName(), event.username);
                }
            };
        }

        disruptor.handleEventsWith(consumers).then(new ClearingEventHandler());

        final RingBuffer<CreateUserEvent> ringBuffer = disruptor.start();

        Thread producer1 = new Thread(() -> {
            Thread.currentThread().setName("lmax-producer-" + Thread.currentThread().getId());
            for (int i = 0; i < 100; i += 2) {
                final long curSeq = ringBuffer.next();
                CreateUserEvent createEvent = ringBuffer.get(curSeq);
                createEvent.username = "user-" + i;
                ringBuffer.publish(curSeq);
                System.out.printf("%s: <== %s\n", Thread.currentThread().getName(), createEvent.username);
            }
        });

        Thread producer2 = new Thread(() -> {
            Thread.currentThread().setName("lmax-producer-" + Thread.currentThread().getId());
            for (int i = 1; i < 100; i += 2) {
                final long curSeq = ringBuffer.next();
                CreateUserEvent createEvent = ringBuffer.get(curSeq);
                createEvent.username = "user-" + i;
                ringBuffer.publish(curSeq);
                System.out.printf("%s: <== %s\n", Thread.currentThread().getName(), createEvent.username);
            }
        });

        producer1.start();
        producer2.start();

        producer1.join();
        producer2.join();

        disruptor.shutdown();

        System.out.println("Main done...");
    }

    private static class ClearingEventHandler implements EventHandler<CreateUserEvent> {
        @Override
        public void onEvent(CreateUserEvent event, long sequence, boolean endOfBatch) {
            event.username = null;
        }
    }

}

