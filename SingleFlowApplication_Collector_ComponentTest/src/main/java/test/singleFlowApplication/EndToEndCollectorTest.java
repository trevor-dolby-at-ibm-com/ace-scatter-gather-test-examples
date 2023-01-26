package test.singleFlowApplication;

import static org.hamcrest.MatcherAssert.assertThat;
import static com.ibm.integration.test.v1.Matchers.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ibm.integration.test.v1.NodeSpy;
import com.ibm.integration.test.v1.SpyObjectReference;
import com.ibm.integration.test.v1.TestMessageAssembly;
import com.ibm.integration.test.v1.TestSetup;

class EndToEndCollectorTest {

    @AfterEach
    public void teardown() throws Exception
    {
    	// Clean up
    	TestSetup.restoreAllMocks();
    }

	@Test
	void httpInvokeTest() throws Exception 
	{
		URL obj = new URL("http://localhost:7800/collectorFlow");
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		//con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
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
	@Test
	void withMockAPISuccessTest() throws Exception 
	{
		// Define the SpyObjectReference and NodeSpy objects
		SpyObjectReference flowRef = new SpyObjectReference().application("SingleFlowApplication").messageFlow("CollectorFlow");
		NodeSpy requestOneSpy = new NodeSpy(flowRef.getURI()+"/nodes/RequestOne");
		NodeSpy requestTwoSpy = new NodeSpy(flowRef.getURI()+"/nodes/RequestTwo");
		NodeSpy replyOneSpy = new NodeSpy(flowRef.getURI()+"/nodes/ReplyOne");
		NodeSpy replyTwoSpy = new NodeSpy(flowRef.getURI()+"/nodes/ReplyTwo");

		NodeSpy httpInputSpy = new NodeSpy(flowRef.getURI()+"/nodes/HTTP%20Input");
		NodeSpy httpReplySpy = new NodeSpy(flowRef.getURI()+"/nodes/HTTP%20Reply");
		NodeSpy createReplySpy = new NodeSpy(flowRef.getURI()+"/nodes/CreateReply");

		requestOneSpy.setStopAtInputTerminal("in");
		requestTwoSpy.setStopAtInputTerminal("in");

		// We need a future that will complete when the flow propagates to the HTTPReply
		// node, and to do this we put the trigger on the node before it in the flow.
		CompletableFuture<Void> propagateFutureCreateReply = createReplySpy.whenPropagateCountIs(1);
	
		// Declare a new TestMessageAssembly object for the message being sent into the node
		TestMessageAssembly httpInputSpyMA = new TestMessageAssembly();
		httpInputSpyMA.buildFromRecordedMessageAssembly(Thread.currentThread()
				.getContextClassLoader().getResourceAsStream("/00009C50-63A5D3BF-00000001-0.mxml"));
		httpInputSpy.propagate(httpInputSpyMA, "out");
		System.out.println("Input message sent");
		
		TestMessageAssembly replyOneSpyMA = new TestMessageAssembly();
		replyOneSpyMA.buildFromRecordedMessageAssembly(Thread.currentThread()
				.getContextClassLoader().getResourceAsStream("/000096DC-63A5D3BF-00000001-0.mxml"));
		replyOneSpy.propagate(replyOneSpyMA, "out");
		System.out.println("HTTP Async Response message one sent");

		TestMessageAssembly replyTwoSpyMA = new TestMessageAssembly();
		replyTwoSpyMA.buildFromRecordedMessageAssembly(Thread.currentThread()
				.getContextClassLoader().getResourceAsStream("/000048A4-63A5D3BF-00000001-0.mxml"));
		replyTwoSpy.propagate(replyTwoSpyMA, "out");
		System.out.println("HTTP Async Response message two sent");

		// Wait for the flow to complete
		propagateFutureCreateReply.get(10, TimeUnit.SECONDS);
		
		// Make sure that we don't outrun the flow itself!
		Thread.sleep(100);
		
		// Validate the results from the flow execution
		
        // We will now pick up the message that is propagated into the "HttpReply" node and validate it
		TestMessageAssembly replyMessageAssembly = httpReplySpy.receivedMessageAssembly("in", 1);

        // Assert that the actual message tree matches the expected message tree
		TestMessageAssembly expectedMessageAssembly = new TestMessageAssembly();
		expectedMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("/000088B8-63A5D3BF-00000002-1.mxml"));

        assertThat(replyMessageAssembly, equalsMessage(expectedMessageAssembly).ignoreTimeStamps()
        		.ignorePath("/Message/Properties/CodedCharSetId", false)); // Different platforms may have different defaults
	}
}
