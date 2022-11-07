package org.max.lmax.disruptor;

import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * java -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=print,Main.sum Main
 */
public class LoadStoreFences {

    /*
    counter: 646235
    counter: 726424
    counter: 728293
    counter: 617362
    counter: 594006


    counter: 781574
    counter: 677565
    counter: 759176
    counter: 632863
    counter: 866438
     */
    private static final int MAX_VALUE = 1_000_000;

    private static int SHARED_COUNTER = 0;

    public static void main(String[] args) throws Exception {

        Thread firstHalf = new Thread(() -> {
            for (int i = 0; i < MAX_VALUE / 2; ++i) {
                VarHandle.loadLoadFence();
                ++SHARED_COUNTER;
                VarHandle.storeStoreFence();
            }
        });

        Thread secondHalf = new Thread(() -> {
            for (int i = MAX_VALUE / 2; i < MAX_VALUE; ++i) {
                VarHandle.loadLoadFence();
                ++SHARED_COUNTER;
                VarHandle.storeStoreFence();
            }
        });

        firstHalf.start();
        secondHalf.start();

        firstHalf.join();
        secondHalf.join();

        System.out.printf("counter: %d\n", SHARED_COUNTER);

        System.out.println("Main done...");
    }


}

