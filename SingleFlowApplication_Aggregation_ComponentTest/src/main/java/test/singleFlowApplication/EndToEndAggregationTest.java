package test.singleFlowApplication;

import static org.hamcrest.MatcherAssert.assertThat;
import static com.ibm.integration.test.v1.Matchers.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ibm.integration.test.v1.NodeSpy;
import com.ibm.integration.test.v1.SpyObjectReference;
import com.ibm.integration.test.v1.TestMessageAssembly;
import com.ibm.integration.test.v1.TestSetup;
import com.ibm.integration.test.v1.exception.TestException;


class EndToEndAggregationTest {
    @AfterEach
    public void teardown() throws Exception
    {
      // Clean up
      TestSetup.restoreAllMocks();
    }

  @Test
  void storedMessageTest() throws TestException, InterruptedException, ExecutionException, TimeoutException 
  {
    // Define the SpyObjectReference and NodeSpy objects
    SpyObjectReference flowRef = new SpyObjectReference().application("SingleFlowApplication").messageFlow("AggregationFlow");
    NodeSpy httpInputSpy = new NodeSpy(flowRef.getURI()+"/nodes/HTTP%20Input");
    NodeSpy httpReplySpy = new NodeSpy(flowRef.getURI()+"/nodes/HTTP%20Reply");
    NodeSpy completeSpy = new NodeSpy(flowRef.getURI()+"/nodes/Complete");

        // Assert that the actual message tree matches the expected message tree
    TestMessageAssembly expectedMessageAssembly = new TestMessageAssembly();
    expectedMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow-HTTPReply-00003344_63A4992A_00000001_2.mxml"));

    // Configure the "in" terminal on the HTTP Reply node not to propagate.
    // If we don't do this, then the reply node will throw exceptions when it  
    // realises we haven't actually used the HTTP transport.
    httpReplySpy.setStopAtInputTerminal("in");

    // We need a future that will complete when the flow propagates to the HTTPReply
    // node, and to do this we put the trigger on the node before it in the flow.
    CompletableFuture<Void> propagateFutureComplete = completeSpy.whenPropagateCountIs(1);
    
    // Now call propagate on the "out" terminal of the HTTP Input node.
    // This takes the place of an actual HTTP message: we simple hand the node
    // the message assembly and tell it to propagate that as if it came from an
    // actual client. This line is where the flow is actually run.
    TestMessageAssembly httpInputMessageAssembly = new TestMessageAssembly();
    httpInputMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow-HTTPInput-00009150_63A4992A_00000001_0.mxml"));
    httpInputSpy.propagate(httpInputMessageAssembly, "out");
    System.out.println("Message sent");

    // Wait for the flow to complete
    propagateFutureComplete.get(30, TimeUnit.SECONDS);
    
    // Validate the results from the flow execution
    
    // Make sure that we don't outrun the flow itself!
    Thread.sleep(100);

        // We will now pick up the message that is propagated into the "HttpReply" node and validate it
    TestMessageAssembly replyMessageAssembly = httpReplySpy.receivedMessageAssembly("in", 1);

        assertThat(replyMessageAssembly, equalsMessage(expectedMessageAssembly));
  }

  @Test
  void httpInvokeTest() throws Exception 
  {
    URL obj = new URL("http://localhost:7800/aggregationFlow");
    HttpURLConnection con = (HttpURLConnection) obj.openConnection();
    con.setRequestMethod("GET");
    //con.setRequestProperty("Content-Type", "application/json; utf-8");
    //if (payload != null) {
        //  con.setDoOutput(true);
        //  try (OutputStream os = con.getOutputStream()) {
        //     byte[] input = payload.getBytes("utf-8");
        //     os.write(input, 0, input.length);
        //  }
      //}
    int responseCode = con.getResponseCode();
    assertThat(responseCode,
            Matchers.anyOf(Matchers.is(HttpURLConnection.HTTP_ACCEPTED), Matchers.is(HttpURLConnection.HTTP_OK)));
    con.disconnect();
  }
}
