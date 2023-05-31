package test.singleFlowApplication;

import static com.ibm.integration.test.v1.Matchers.equalsMessage;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ibm.integration.test.v1.NodeSpy;
import com.ibm.integration.test.v1.NodeStub;
import com.ibm.integration.test.v1.SpyObjectReference;
import com.ibm.integration.test.v1.TestMessageAssembly;
import com.ibm.integration.test.v1.TestSetup;
import com.ibm.integration.test.v1.exception.TestException;

public class AggregationFlow_EndToEndTestWithMocks
{
    @AfterEach
    public void teardown() throws Exception
    {
      // Clean up
      TestSetup.restoreAllMocks();
    }
  @Test
  void firstHalfTest() throws TestException, InterruptedException 
  {
    // Define the SpyObjectReference and NodeSpy objects
    SpyObjectReference flowRef = new SpyObjectReference().application("AggregationApplication").messageFlow("AggregationFlow");
    NodeSpy httpInputSpy = new NodeSpy(flowRef.getURI()+"/nodes/HTTP%20Input");
    NodeStub aggregateControlStub = new NodeStub(flowRef.getURI()+"/nodes/Aggregate%20Control");
    NodeSpy mqOutput1Spy = new NodeSpy(flowRef.getURI()+"/nodes/MQ%20Output1");
    NodeSpy mqOutput2Spy = new NodeSpy(flowRef.getURI()+"/nodes/MQ%20Output2");

    // Declare a new TestMessageAssembly object for the message being sent into the node
    TestMessageAssembly inputMessageAssembly = new TestMessageAssembly();
    inputMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow_00007DD8_63A4DDF9_00000001_0.mxml"));

    mqOutput1Spy.setStopAtInputTerminal("in");
    mqOutput2Spy.setStopAtInputTerminal("in");

    

    // Program the stub to return this dummy result instead of calling the service
    TestMessageAssembly aggregateControlMessage = new TestMessageAssembly();
    aggregateControlMessage.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow_00007DD8_63A4DDF9_00000001_1.mxml"));
    aggregateControlStub.onCall().propagatesMessage("in", "out", aggregateControlMessage);
    
    
    
    // Now call propagate on the "out" terminal of the HTTP Input node.
    // This takes the place of an actual HTTP message: we simple hand the node
    // the message assembly and tell it to propagate that as if it came from an
    // actual client. This line is where the flow is actually run.
    httpInputSpy.propagate(inputMessageAssembly, "out");


    // Validate the results from the flow execution
        // We will now pick up the message that is propagated into the first MQOutput node and validate it
    TestMessageAssembly replyMessageAssembly = mqOutput1Spy.receivedMessageAssembly("in", 1);
    TestMessageAssembly expectedMessageAssembly = new TestMessageAssembly();
    expectedMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow_00007DD8_63A4DDF9_00000001_5.mxml"));
        assertThat(replyMessageAssembly, equalsMessage(expectedMessageAssembly));

        // Validate the second MQOutput node message
        replyMessageAssembly = mqOutput2Spy.receivedMessageAssembly("in", 1);
    expectedMessageAssembly = new TestMessageAssembly();
    expectedMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow_00007DD8_63A4DDF9_00000001_2.mxml"));
        assertThat(replyMessageAssembly, equalsMessage(expectedMessageAssembly));
  }
  
  @Test
  void secondHalfTest() throws TestException, InterruptedException 
  {
    // Define the SpyObjectReference and NodeSpy objects
    SpyObjectReference flowRef = new SpyObjectReference().application("AggregationApplication").messageFlow("AggregationFlow");
    NodeSpy httpReplySpy = new NodeSpy(flowRef.getURI()+"/nodes/HTTP%20Reply");
    NodeSpy aggregateReplySpy = new NodeSpy(flowRef.getURI()+"/nodes/Aggregate%20Reply");

    // Declare a new TestMessageAssembly object for the message being sent into the node
    TestMessageAssembly inputMessageAssembly = new TestMessageAssembly();
    inputMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow_00007024_63A4DDF9_00000001_1.mxml"));

    httpReplySpy.setStopAtInputTerminal("in");
    
    // Now call propagate on the "out" terminal of the Aggregate Reply node.
    // This takes the place of actual aggregation: we simple hand the node
    // the message assembly and tell it to propagate that as if it came from an
    // actual client. This line is where the flow is actually run.
    aggregateReplySpy.propagate(inputMessageAssembly, "out");

    // Validate the results from the flow execution
        // We will now pick up the message that is propagated into the "HttpReply" node and validate it
    TestMessageAssembly replyMessageAssembly = httpReplySpy.receivedMessageAssembly("in", 1);

        // Assert that the actual message tree matches the expected message tree
    TestMessageAssembly expectedMessageAssembly = new TestMessageAssembly();
    expectedMessageAssembly.buildFromRecordedMessageAssembly(Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("/AggregationFlow_00007024_63A4DDF9_00000001_2.mxml"));
        assertThat(replyMessageAssembly, equalsMessage(expectedMessageAssembly));
  }
}
