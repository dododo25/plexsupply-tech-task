package com.dododo.plexsupply;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("app.jar [nThreads] [filepath]");
            return;
        }

        int nThreads = Integer.parseInt(args[0]);

        if (nThreads <= 0) {
            System.err.println("nThreads value must be greater that zero!");
            return;
        }

        Path path = Path.of(args[1]);

        Deque<Input> inputDeque = new ConcurrentLinkedDeque<>();

        initThreads(inputDeque, nThreads, Thread.currentThread());

        try (Stream<String> lines = Files.lines(path)) {
            AtomicLong lineIndex = new AtomicLong();
            lines.forEach(line -> inputDeque.add(new Input(line, lineIndex.getAndIncrement())));
        }
    }

    private static void initThreads(Deque<Input> inputDeque, int nThreads, Thread mainThread) {
        ConcurrentSkipListMap<Long, String> outputMap = new ConcurrentSkipListMap<>();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(nThreads + 1);

        for (int i = 0; i < nThreads; i++) {
            executor.scheduleAtFixedRate(() -> {
                if (inputDeque.isEmpty()) {
                    return;
                }

                Input input = inputDeque.pop();

                try {
                    int value = Integer.parseInt(input.value());
                    BigInteger factorial = calculateFactorial(value);

                    outputMap.put(input.index(), String.format("%d = %d\n", value, factorial));
                } catch (NumberFormatException e) {
                    outputMap.put(input.index(), String.format("%s\n", input.value()));
                }
            }, 0, nThreads * 10L, TimeUnit.MILLISECONDS);
        }

        executor.submit(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("out.txt"))) {
                long lastIndex = 0;

                while (mainThread.isAlive() || !inputDeque.isEmpty() || !outputMap.isEmpty()) {
                    while (!outputMap.isEmpty()) {
                        Map.Entry<Long, String> entry = outputMap.firstEntry();

                        if (lastIndex != entry.getKey()) {
                            continue;
                        }

                        writer.write(entry.getValue());
                        writer.flush();

                        outputMap.remove(entry.getKey());
                        lastIndex++;
                    }
                }
            }

            executor.shutdown();
            return null;
        });
    }

    private static BigInteger calculateFactorial(int v) {
        if (v < 0) {
            throw new IllegalArgumentException();
        }

        if (v < 2) {
            return BigInteger.ONE;
        }

        BigInteger res = BigInteger.ONE;

        for (int i = 2; i <= v; i++) {
            res = res.multiply(BigInteger.valueOf(i));
        }

        return res;
    }

    public record Input(String value, long index) {

    }
}
