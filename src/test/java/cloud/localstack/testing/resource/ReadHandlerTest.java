package cloud.localstack.testing.resource;

import com.amazonaws.services.sns.model.ListSubscriptionsByTopicRequest;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult;
import com.amazonaws.services.sns.model.Subscription;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest {

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
        String queueArn = "arn:aws:sqs:us-east-1:123456789012:my-queue";
        Map<String, String> queueAttributes = Collections.singletonMap("QueueArn", queueArn);
        doReturn(new GetQueueAttributesResult().withAttributes(queueAttributes)).when(proxy).<GetQueueAttributesRequest, GetQueueAttributesResult>injectCredentialsAndInvoke(any(GetQueueAttributesRequest.class), any());

        String subscriptionArn = "arn:aws:sns:us-east-1:123456789012:my-topic:sub-id";
        Subscription subscription = new Subscription()
                .withProtocol("sqs")
                .withEndpoint(queueArn)
                .withSubscriptionArn(subscriptionArn);
        doReturn(new ListSubscriptionsByTopicResult().withSubscriptions(Collections.singletonList(subscription))).when(proxy).<ListSubscriptionsByTopicRequest, ListSubscriptionsByTopicResult>injectCredentialsAndInvoke(any(ListSubscriptionsByTopicRequest.class), any());

        final ReadHandler handler = new ReadHandler();

        String topicArn = "arn:aws:sns:us-east-1:123456789012:my-topic";
        String queueUrl = "http://localhost:4566/123456789012/my-queue";
        final ResourceModel model = ResourceModel.builder().topicArn(topicArn).queueUrl(queueUrl).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
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
