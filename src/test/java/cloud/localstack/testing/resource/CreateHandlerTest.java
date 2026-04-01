package cloud.localstack.testing.resource;

import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.proxy.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_SimpleSuccess() {

        String queueUrl = "http://localhost:4566/foo";
        doReturn(new CreateQueueResult().withQueueUrl(queueUrl)).when(proxy).<CreateQueueRequest, CreateQueueResult>injectCredentialsAndInvoke(any(CreateQueueRequest.class), any());

        String queueArn = "arn:aws:...";
        Map<String, String> queueAttributes = Collections.singletonMap("QueueArn", queueArn);
        doReturn(new GetQueueAttributesResult().withAttributes(queueAttributes)).when(proxy).<GetQueueAttributesRequest, GetQueueAttributesResult>injectCredentialsAndInvoke(any(GetQueueAttributesRequest.class), any());

        String subscriptionArn = "arn:aws:...";
        doReturn(new SubscribeResult().withSubscriptionArn(subscriptionArn)).when(proxy).<SubscribeRequest, SubscribeResult>injectCredentialsAndInvoke(any(SubscribeRequest.class), any());

        doReturn(new SetQueueAttributesResult()).when(proxy).<SetQueueAttributesRequest, SetQueueAttributesResult>injectCredentialsAndInvoke(any(SetQueueAttributesRequest.class), any());

        final CreateHandler handler = new CreateHandler();

        String topicArn = "arn:aws:...";
        final ResourceModel model = ResourceModel.builder().topicArn(topicArn).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .clientRequestToken(UUID.randomUUID().toString())
                .logicalResourceIdentifier("MyQueue")
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
