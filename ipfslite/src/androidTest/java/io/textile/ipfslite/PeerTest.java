package io.textile.ipfslite;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.apache.commons.io.FileUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Textile tests.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PeerTest {


    private final static Logger logger =
            Logger.getLogger("TEST");

    static String COMMON_CID = "QmWATWQ7fVPP2EFGu71UkfnqhYXDYH566qy47CnJDgvs8u";
    static String HELLO_WORLD_CID = "bafybeic35nent64fowmiohupnwnkfm2uxh6vpnyjlt3selcodjipfrokgi";
    static String HELLO_WORLD = "Hello World";
    static String TEST1_CID = "bafybeifi4myu2s6rkegzeb2qk6znfg76lt4gpqe6sftozg3rjy6a5cw4qa";
    static String REPO_NAME = "ipfslite";

    static Peer litePeer;

    String createRepo(Boolean reset) throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        final File filesDir = ctx.getFilesDir();
        final String path = new File(filesDir, REPO_NAME).getAbsolutePath();
        // Wipe repo
        File repo = new File(path);
        if (repo.exists() && reset == true) {
            FileUtils.deleteDirectory(repo);
        }
        return path;
    }

    void startPeer() throws Exception {
        // Initialize & start
        litePeer = new Peer(createRepo(true), BuildConfig.DEBUG);
        litePeer.start();
    }

    @Test
    public void startTest() throws Exception {
        startPeer();
        assertEquals(true, litePeer.started());
    }

    @Test
    public void GetFileSync() throws Exception {
        if (litePeer == null) {
            startPeer();
            assertEquals(true, litePeer.started());
        }

        byte[] file = litePeer.getFileSync(COMMON_CID);

        assertNotNull(file);
    }
    @Test
    public void GetFile() throws Exception {
        if (litePeer == null) {
            startPeer();
            assertEquals(true, litePeer.started());
        }

        // Make the CID locally available
        litePeer.addFileSync(HELLO_WORLD.getBytes());

        final CountDownLatch finishLatch = new CountDownLatch(1);

        // byte array to build
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Async call
        litePeer.getFile(
                HELLO_WORLD_CID, new Peer.GetFileHandler() {
                    @Override
                    public void onNext(byte[] data) {
                        try {
                            logger.log(Level.INFO, "" + data.length);
                            output.write(data);
                        } catch (Throwable t) {
                            assertNull(t);
                            finishLatch.countDown();
                        }

                    }

                    @Override
                    public void onError(Throwable t) {
                        assertNull(t);
                        finishLatch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        byte[] result = output.toByteArray();
                        try {
                            String resultString = new String(result, "UTF-8");
                            assertEquals(HELLO_WORLD, resultString);
                        } catch (Throwable t) {
                            assertNull(t);
                        }
                        finishLatch.countDown();
                    }
                });
        // Await async call
        finishLatch.await();
    }


    @Test
    public void AddFileSync() throws Exception {
        if (litePeer == null) {
            startPeer();
            assertEquals(true, litePeer.started());
        }
        String cid = litePeer.addFileSync(HELLO_WORLD.getBytes());
        assertEquals(HELLO_WORLD_CID, cid);

        byte[] res = litePeer.getFileSync(HELLO_WORLD_CID);
        assertEquals(HELLO_WORLD, new String(res, "UTF-8"));
    }

    @Test
    public void AddFile() throws Exception {
        if (litePeer == null) {
            startPeer();
            assertEquals(true, litePeer.started());
        }

        // Make the CID locally available
        litePeer.addFileSync(HELLO_WORLD.getBytes());

        AtomicBoolean ready = new AtomicBoolean();
        final AtomicReference<String> CID = new AtomicReference<>("");

        // Async call
        litePeer.addFile(
            HELLO_WORLD.getBytes(), new Peer.AddFileHandler() {
                @Override
                public void onNext(String cid) {
                    CID.set(cid);
                }

                @Override
                public void onError(Throwable t) {
                    assertNull(t);
                    ready.getAndSet(true);
                }

                @Override
                public void onComplete() {
                    String result = CID.get();
                    assertEquals(HELLO_WORLD_CID, result);
                    ready.getAndSet(true);
                }
            });
        // Await async call
        await().atMost(30, TimeUnit.SECONDS).untilTrue(ready);
    }


    @Test
    public void AddThenGetImage() throws Exception {
        if (litePeer == null) {
            startPeer();
            assertEquals(true, litePeer.started());
        }

        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input1 = PeerTest.getCacheFile(ctx, "TEST1.JPG");

        byte[] fileBytes = Files.readAllBytes(input1.toPath());
        String cid = litePeer.addFileSync(fileBytes);
        assertEquals(TEST1_CID, cid);

        byte[] res = litePeer.getFileSync(TEST1_CID);
        assertArrayEquals(fileBytes, res);
    }

    private static File getCacheFile(Context context, String filename) throws IOException {
        File file = new File(context.getCacheDir(), filename);
        InputStream inputStream = context.getAssets().open(filename);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            try {
                byte[] buf = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) > 0) {
                    outputStream.write(buf, 0, len);
                }
            } finally {
                outputStream.close();
            }
        }
        return file;
    }
}
