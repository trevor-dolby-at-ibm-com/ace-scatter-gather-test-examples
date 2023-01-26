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
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ibm.integration.test.v1.NodeSpy;
import com.ibm.integration.test.v1.SpyObjectReference;
import com.ibm.integration.test.v1.TestMessageAssembly;
import com.ibm.integration.test.v1.TestSetup;
import com.ibm.integration.test.v1.exception.TestException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.MQConstants;

class EndToEndCollectorTest {

	// These are need prior to 12.0.8 to avoid finalizer-related issues
	public static TestMessageAssembly httpInputSpyMA;
	public static TestMessageAssembly replyOneSpyMA;
	public static TestMessageAssembly replyTwoSpyMA;
	public EndToEndCollectorTest()
	{
		try
		{
			httpInputSpyMA = new TestMessageAssembly();
			replyOneSpyMA = new TestMessageAssembly();
			replyTwoSpyMA = new TestMessageAssembly();
		}
		catch ( TestException te )
		{
			te.printStackTrace();
		}
	}
    @BeforeAll
    public static void setupTestCase() throws Exception 
    {
       	if ( System.getenv("MQSI_FORCE_NONSHARED_MQ_CONNECTIONS") == null )
       	{
       		System.out.println("Need MQSI_FORCE_NONSHARED_MQ_CONNECTIONS=1 to enable connection re-use");
       		throw new RuntimeException("Need MQSI_FORCE_NONSHARED_MQ_CONNECTIONS=1 to enable connection re-use");
       	}
    }
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
	   public class PropagateAndCommit extends Thread
	   {
	    	NodeSpy ns;
	    	String terminal;
	    	TestMessageAssembly tma;
	    	public PropagateAndCommit(NodeSpy ns, TestMessageAssembly tma , String terminal) throws TestException
	    	{
	    		this.ns = ns;
	    		this.tma = tma;
	    		this.terminal = terminal;
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

		System.gc();
		
		// Declare a new TestMessageAssembly object for the message being sent into the node
		httpInputSpyMA.buildFromRecordedMessageAssembly(Thread.currentThread()
				.getContextClassLoader().getResourceAsStream("/00009C50-63A5D3BF-00000001-0.mxml"));
		PropagateAndCommit pAndC = new PropagateAndCommit(httpInputSpy, httpInputSpyMA ,"out");
		pAndC.start(); pAndC.join(10000);
		System.out.println("Input message sent");
		System.gc();
		
		replyOneSpyMA.buildFromRecordedMessageAssembly(Thread.currentThread()
				.getContextClassLoader().getResourceAsStream("/000096DC-63A5D3BF-00000001-0.mxml"));
		PropagateAndCommit pAndCOne = new PropagateAndCommit(replyOneSpy, replyOneSpyMA ,"out");
		pAndCOne.start(); pAndCOne.join(10000);
		System.out.println("HTTP Async Response message one sent");
		System.gc();

		replyTwoSpyMA.buildFromRecordedMessageAssembly(Thread.currentThread()
				.getContextClassLoader().getResourceAsStream("/000048A4-63A5D3BF-00000001-0.mxml"));
		PropagateAndCommit pAndCTwo = new PropagateAndCommit(replyTwoSpy, replyTwoSpyMA ,"out");
		pAndCTwo.start(); pAndCTwo.join(10000);
		System.out.println("HTTP Async Response message two sent");
		System.gc();

		// Wait for the flow to complete
		propagateFutureCreateReply.get(10, TimeUnit.SECONDS);
		
		// Make sure that we don't outrun the flow itself!
		Thread.sleep(100);
		
		// Validate the results from the flow execution
		
        // We will now pick up the message that is propagated into the "HttpReply" node and validate it
		TestMessageAssembly replyMessageAssembly = httpReplySpy.receivedMessageAssembly("in", 1);

		//System.gc();

        // Assert that the actual message tree matches the expected message tree
		TestMessageAssembly expectedMessageAssembly = new TestMessageAssembly();
		expectedMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("/000088B8-63A5D3BF-00000002-1.mxml"));

        assertThat(replyMessageAssembly, equalsMessage(expectedMessageAssembly).ignoreTimeStamps()
        		.ignorePath("/Message/Properties/CodedCharSetId", false)); // Different platforms may have different defaults
		//System.gc();
        
        // This shouldn't be needed, but Java sometimes gets confused and runs 
        // the finalizers too early if we don't refer to the mock here.
        createReplySpy.restore();
		requestOneSpy.restore();
		requestTwoSpy.restore();
		replyOneSpy.restore();
		replyTwoSpy.restore();
	}
}
