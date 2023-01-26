package test.singleFlowApplication;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static com.ibm.integration.test.v1.Matchers.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ibm.integration.test.v1.NodeSpy;
import com.ibm.integration.test.v1.SpyObjectReference;
import com.ibm.integration.test.v1.TestMessageAssembly;
import com.ibm.integration.test.v1.TestSetup;
import com.ibm.integration.test.v1.exception.TestException;


import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.MQConstants;


class EndToEndAggregationTest {
    @BeforeAll
    public static void setupTestCase() throws Exception 
    {
       	if ( System.getenv("MQSI_FORCE_NONSHARED_MQ_CONNECTIONS") == null )
       	{
       		System.out.println("Need MQSI_FORCE_NONSHARED_MQ_CONNECTIONS=1 to enable connection re-use");
       		throw new RuntimeException("Need MQSI_FORCE_NONSHARED_MQ_CONNECTIONS=1 to enable connection re-use");
       	}
    }
    
    public class PropagateAndCommit extends Thread
    {
    	NodeSpy ns;
    	String terminal;

    	TestMessageAssembly tma;
    	public PropagateAndCommit(NodeSpy ns, String messageName, String terminal) throws TestException
    	{
    		this.ns = ns;
    		this.terminal = terminal;
    		
    		// Declare a new TestMessageAssembly object for the message being sent into the node
    		this.tma = new TestMessageAssembly();
    		InputStream messageStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(messageName);
    		tma.buildFromRecordedMessageAssembly(messageStream);
    	}
    	@Override
    	public void run()
    	{
    		try {
    			MQQueueManager qmConnection = new MQQueueManager(System.getProperty("broker.qmgr"),  MQConstants.MQCNO_STANDARD_BINDING + CMQC.MQCNO_HANDLE_SHARE_NONE);
                System.out.println("Connected to default queue manager "+System.getProperty("broker.qmgr"));
                
				ns.propagate(tma, terminal);
				qmConnection.commit();
                System.out.println("Transaction comitted");
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    }
    @AfterEach
    public void teardown() throws Exception
    {
    	// Clean up
    	TestSetup.restoreAllMocks();
    }

	@Test
	void storedMessageTest() throws TestException, InterruptedException, MQException, ExecutionException, TimeoutException 
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
		PropagateAndCommit pAndC = new PropagateAndCommit(httpInputSpy, 
				"/AggregationFlow-HTTPInput-00009150_63A4992A_00000001_0.mxml", "out");
		pAndC.start();
		pAndC.join(10000);
		System.out.println("Message sent");
		// Wait for the flow to complete
		propagateFutureComplete.get(10, TimeUnit.SECONDS);
		
		// Validate the results from the flow execution
		
		// Make sure that we don't outrun the flow itself!
		Thread.sleep(100);

        // We will now pick up the message that is propagated into the "HttpReply" node and validate it
		TestMessageAssembly replyMessageAssembly = httpReplySpy.receivedMessageAssembly("in", 1);

        assertThat(replyMessageAssembly, equalsMessage(expectedMessageAssembly));
        
        // This shouldn't be needed, but Java sometimes gets confused and runs 
        // the finalizers too early if we don't refer to the mock here.
        completeSpy.restore();
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
