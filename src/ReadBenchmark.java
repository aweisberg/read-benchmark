import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


public class ReadBenchmark {
    private static final List<ByteBuffer> mappings = new CopyOnWriteArrayList<>();
    private static final List<AtomicLong> counters = new CopyOnWriteArrayList<>();

    private static final ThreadLocal<AtomicLong> countersLocal = new ThreadLocal<AtomicLong>() {
        @Override
        public AtomicLong initialValue() {
            AtomicLong retval = new AtomicLong();
            counters.add(retval);
            return retval;
        }
    };

    //Target for storing the checksum to prevent dead code elimination
    private static byte unused;

//  /*
//  * Very expensive to compute constants for Zipfian distributions with different bias
//  * zetans
//  * .999999 - 23.603331618973705
//  * .99999 - 23.605717019208985
//  * .9999 - 23.62958916236007
//  * .999 - 23.870135124976315
//  * .99 - 26.46902820178302
//  * .95 - 43.81911600654099
//  * .90 - 90.56988598108148
//  * .8 - 495.5624615889921
//  * .5 - 199998.53965056606
//  */

    /*
     * First arg is path to the preallocated file
     * Second is the size of the preallocated
     * Third is the portion of the file that will be read from
     * Fourth is the duration in seconds
     * Fifth is how often to log performance in seconds
     * Sixth is the number of concurrent IO threads
     */
    public static void main(String[] args) throws Exception {
        final String path = args[0];
        final long filesize = Integer.valueOf(args[1]) * 1024L * 1024L * 1024L;
        final long usedsize = Integer.valueOf(args[2]) * 1024L * 1024L * 1024L;
        final int duration = Integer.valueOf(args[3]);
        final int reportinterval = Integer.valueOf(args[4]);
        final int threads = Integer.valueOf(args[5]);

        System.out.println("Benchmarking file size " + args[1] + "gb and using " + args[2] + "gb with " + threads + " threads");
        File f = new File(path, "benchmark.bin");
        f.createNewFile();


        int buffersize = 1024 * 64;
        //Make sure the file is the the right minimum length
        if (f.length() < filesize) {
            System.out.println("Preallocating large file, this could take a while");
            FileOutputStream fos = new FileOutputStream(f, true);
            ByteBuffer buf = ByteBuffer.allocateDirect(buffersize);
            FileChannel fc = fos.getChannel();
            long size = 0;
            while ((size = fc.size()) < filesize) {
                buf.clear();
                if (filesize - size < buffersize) {
                    buf.limit((int)(filesize - size));
                }
                while (buf.hasRemaining()) {
                    fc.write(buf);
                }
            }
            fc.force(true);
            System.out.println("Finished preallocating large file");
        }

        /*
         * Map the entire file
         */
        FileInputStream fis = new FileInputStream(f);
        FileChannel fc = fis.getChannel();
        long mapped = 0;
        final long mappingsize = 1024L * 1024L * 1024L;
        while (mapped < filesize) {
            if (filesize - mapped >= mappingsize) {
                mappings.add(fc.map(MapMode.READ_ONLY, mapped, mappingsize));
                mapped += mappingsize;
            } else {
                System.err.println("Thing didn't work out");
                System.exit(-1);
            }
        }

        //Kick off executors to do reads through the file with a Zipfian distribution
        final AtomicBoolean shouldContinue = new AtomicBoolean(true);
        List<Future<Long>> tasks = new ArrayList<>();
        ExecutorService es = Executors.newFixedThreadPool(threads);
        for (int ii = 0; ii < threads; ii++) {
            tasks.add(es.submit(new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    final AtomicLong counter = countersLocal.get();
                    counter.set(0);
                    final ThreadLocalRandom tlr = ThreadLocalRandom.current();
                    final ScrambledZipfianGenerator zg = new ScrambledZipfianGenerator(0, usedsize / 1024L);

                    byte checksum = 0;
                    while (shouldContinue.get()) {
                        final long toRead = zg.nextLong();
                        final ByteBuffer buf = mappings.get((int)((toRead * 1024L) / mappingsize)).duplicate();
                        final int position = (int)((toRead * 1024L) % mappingsize);
                        buf.position(position);
                        buf.limit(buf.position() + 1024);
                        while(buf.hasRemaining()) {
                            checksum ^= buf.get();
                        }
                        counter.incrementAndGet();
                    }

                    if (unused != 0) {
                        unused = checksum;
                    }

                    return null;
                }
            }));
        }

        //warmup
        System.out.println("Running " + duration + " second warmup");
        runReportingLoop(duration, reportinterval, System.currentTimeMillis());
        System.out.println("Finished " + duration + " second warmup");

        for (AtomicLong subcount : counters) {
            subcount.set(0);
        }

        //Run the actual benchmark
        final long benchmarkStart = System.currentTimeMillis();
        runReportingLoop(duration, reportinterval, benchmarkStart);

        shouldContinue.set(false);

        try {
            for (Future<Long> task : tasks) {
                task.get();
            }
        } finally {
            es.shutdown();
        }

        final long benchmarkDelta = System.currentTimeMillis() - benchmarkStart;

        long sum = 0;
        for (AtomicLong subcount : counters) {
            sum += subcount.get();
        }

        System.out.printf("Did %d reads in %.2f seconds at a rate of %.2f reads/second\n", sum, (benchmarkDelta / 1000.0), (sum / (benchmarkDelta / 1000.0)));
    }

private static void runReportingLoop(final int duration,
        final int reportinterval, final long benchmarkStart) throws InterruptedException {
    long sum = 0;
    for (int ii = 0; ii < duration / reportinterval; ii++) {
        final long start = System.currentTimeMillis();
        Thread.sleep(TimeUnit.SECONDS.toMillis(reportinterval));
        long newsum = 0;
        for (AtomicLong subcount : counters) {
            newsum += subcount.get();
        }

        final long now = System.currentTimeMillis();
        final long deltaTime = now - start;

        long delta = newsum - sum;
        sum = newsum;
        System.out.printf("At %d did %.2f reads/second\n", TimeUnit.MILLISECONDS.toSeconds(now - benchmarkStart), delta / (deltaTime / 1000.0));
    }
}

}
